package com.rinda.backend.monitoring;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rinda.backend.analysis.MlClient.MlCallException;
import com.rinda.backend.common.ThresholdService;
import com.rinda.backend.config.RindaProperties;
import com.rinda.backend.monitoring.SatelliteClient.HistoryRow;
import com.rinda.backend.monitoring.SatelliteClient.MonitorRequest;
import com.rinda.backend.plot.FlagType;
import com.rinda.backend.plot.PlotFlags;
import com.rinda.backend.plot.PlotRepository;
import com.rinda.backend.plot.PlotRules;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Satellite monitoring jobs (docs/02-satellite-monitoring.md):
 * <ul>
 *   <li>a plot gets its first job only once it has a walked boundary;</li>
 *   <li>BACKFILL fetches from the planting date to today, so a new plot shows its season so far;</li>
 *   <li>the weekly REFRESH fetches what is new since the last fetch (with an overlap for late images);</li>
 *   <li>raw observations are stored per walk, and the season check runs over the whole season after each fetch.</li>
 * </ul>
 * Real plots get Earth Engine data; demo plots only the simulated sample. Nothing falls back to the sample.
 * A season result is a review flag, never proof of crop: INCONSISTENT opens a SATELLITE_INCONSISTENT flag for a
 * person to review, and never rejects a plot.
 *
 * <p>Jobs run on one background thread, which never shares a transaction with the request that queued them.
 * In tests (runInline) the caller waits for that thread.
 */
@Service
public class MonitoringService {

    private static final Logger log = LoggerFactory.getLogger(MonitoringService.class);
    private static final ZoneId KIGALI = ZoneId.of("Africa/Kigali");
    private static final String MODEL_NAME = "season-check";

    private final JdbcTemplate jdbc;
    private final SatelliteClient client;
    private final ObjectMapper mapper;
    private final ThresholdService thresholds;
    private final PlotRepository plots;
    private final PlotRules rules;
    private final PlotFlags flags;
    private final TransactionTemplate tx;
    private final RindaProperties.Monitoring config;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "satellite-monitoring");
        t.setDaemon(true);
        return t;
    });

    public MonitoringService(JdbcTemplate jdbc, SatelliteClient client, ObjectMapper mapper,
                             ThresholdService thresholds, PlotRepository plots, PlotRules rules, PlotFlags flags,
                             TransactionTemplate tx, RindaProperties properties) {
        this.jdbc = jdbc;
        this.client = client;
        this.mapper = mapper;
        this.thresholds = thresholds;
        this.plots = plots;
        this.rules = rules;
        this.flags = flags;
        this.tx = tx;
        this.config = properties.monitoring();
    }

    // ===== Starting jobs =====

    /** A new walk or planting date: queue a job once that change is committed. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMonitoringRequested(MonitoringRequested event) {
        run(() -> queue(event.plotId(), "BOUNDARY").ifPresent(this::execute));
    }

    /** The weekly refresh (decision B). Each run fetches whatever new images exist. */
    @Scheduled(cron = "${rinda.monitoring.cron:-}", zone = "Africa/Kigali")
    public void weeklyRefresh() {
        int queued = refreshAll("SCHEDULE");
        log.info("Weekly satellite refresh queued {} plot(s)", queued);
    }

    /** Queues a job for every walked plot that needs one and runs them in turn. Returns how many were queued. */
    public int refreshAll(String trigger) {
        List<Long> plotIds = jdbc.queryForList("""
                SELECT p.id FROM plots p
                WHERE p.status NOT IN ('ARCHIVED', 'REJECTED')
                  AND EXISTS (SELECT 1 FROM gps_walks w WHERE w.plot_id = p.id AND w.status = 'CURRENT')
                ORDER BY p.id
                """, Long.class);
        List<Long> jobs = new ArrayList<>();
        for (long plotId : plotIds) {
            queue(plotId, trigger).ifPresent(jobs::add);
        }
        run(() -> jobs.forEach(this::execute));
        return jobs.size();
    }

    /** Records a job for the plot's current walk, or nothing when the plot doesn't need one now. */
    Optional<Long> queue(long plotId, String trigger) {
        record Target(String status, boolean demo, LocalDate planting, long walkId) {
        }
        Optional<Target> found = jdbc.query("""
                SELECT p.status, p.is_demo, p.planting_date, w.id
                FROM plots p JOIN gps_walks w ON w.plot_id = p.id AND w.status = 'CURRENT'
                WHERE p.id = ?
                """, (rs, n) -> new Target(rs.getString(1), rs.getBoolean(2), rs.getObject(3, LocalDate.class),
                rs.getLong(4)), plotId).stream().findFirst();
        if (found.isEmpty() || List.of("ARCHIVED", "REJECTED").contains(found.get().status())) {
            return Optional.empty();
        }
        Target plot = found.get();
        Integer busy = jdbc.queryForObject(
                "SELECT COUNT(*) FROM satellite_jobs WHERE plot_id = ? AND status IN ('QUEUED', 'RUNNING')",
                Integer.class, plotId);
        if (busy != null && busy > 0) {
            return Optional.empty();
        }
        record Covered(LocalDate from, LocalDate to) {
        }
        Covered covered = jdbc.queryForObject("""
                SELECT MIN(fetch_from), MAX(fetch_to) FROM satellite_jobs WHERE walk_id = ? AND status = 'DONE'
                """, (rs, n) -> new Covered(rs.getObject(1, LocalDate.class), rs.getObject(2, LocalDate.class)),
                plot.walkId());

        if (plot.demo()) {
            // The simulated sample sets its own dates.
            return Optional.of(insertJob(plotId, plot.walkId(), covered.to() == null ? "BACKFILL" : "REFRESH",
                    "SAMPLE", trigger, null, null));
        }
        if (plot.planting() == null) {
            return Optional.empty();
        }
        LocalDate today = LocalDate.now(KIGALI);
        LocalDate seasonEnd = plot.planting().plusDays(config.maxDaysAfterPlanting());
        LocalDate to = today.isBefore(seasonEnd) ? today : seasonEnd;
        if (to.isBefore(plot.planting())) {
            return Optional.empty();
        }
        if (covered.from() == null || covered.from().isAfter(plot.planting())) {
            return Optional.of(insertJob(plotId, plot.walkId(), "BACKFILL", "GEE", trigger, plot.planting(), to));
        }
        if (!covered.to().isBefore(seasonEnd)) {
            return Optional.empty(); // The season's refresh is over.
        }
        LocalDate from = covered.to().minusDays(config.refreshOverlapDays());
        from = from.isBefore(plot.planting()) ? plot.planting() : from;
        return Optional.of(insertJob(plotId, plot.walkId(), "REFRESH", "GEE", trigger, from, to));
    }

    // ===== Running jobs =====

    void execute(long jobId) {
        record Job(long plotId, long walkId, String source, LocalDate from, LocalDate to) {
        }
        Job job = jdbc.queryForObject("""
                SELECT plot_id, walk_id, source, fetch_from, fetch_to FROM satellite_jobs WHERE id = ?
                """, (rs, n) -> new Job(rs.getLong(1), rs.getLong(2), rs.getString(3),
                rs.getObject(4, LocalDate.class), rs.getObject(5, LocalDate.class)), jobId);
        jdbc.update("UPDATE satellite_jobs SET status = 'RUNNING', started_at = CURRENT_TIMESTAMP(6) WHERE id = ?",
                jobId);
        MonitorRequest request = request(job.plotId(), job.walkId(), job.source(), job.from(), job.to());

        MlCallException last = null;
        for (int attempt = 1; attempt <= config.maxAttempts(); attempt++) {
            jdbc.update("UPDATE satellite_jobs SET attempts = ? WHERE id = ?", attempt, jobId);
            try {
                JsonNode result = client.monitor(request);
                tx.executeWithoutResult(status -> save(jobId, job.plotId(), job.walkId(), result));
                return;
            } catch (MlCallException ex) {
                last = ex;
                log.warn("Satellite job {} for plot {} failed (attempt {}): {}", jobId, job.plotId(), attempt,
                        ex.getMessage());
                if (!ex.retryable()) {
                    break;
                }
            } catch (RuntimeException ex) {
                last = new MlCallException(500, Objects.requireNonNullElse(ex.getMessage(),
                        ex.getClass().getSimpleName()), false);
                log.error("Satellite job {} for plot {} failed unexpectedly", jobId, job.plotId(), ex);
                break;
            }
        }
        String message = Objects.requireNonNullElse(last.getMessage(), "Satellite fetch failed.");
        jdbc.update("""
                UPDATE satellite_jobs SET status = 'FAILED', error_code = ?, error = ?, finished_at = CURRENT_TIMESTAMP(6)
                WHERE id = ?
                """, last.retryable() ? "ML_SERVICE_ERROR" : "REFUSED",
                message.length() > 500 ? message.substring(0, 500) : message, jobId);
    }

    private MonitorRequest request(long plotId, long walkId, String source, LocalDate from, LocalDate to) {
        record Details(String geojson, LocalDate planting) {
        }
        Details plot = jdbc.queryForObject("SELECT geojson, planting_date FROM plots WHERE id = ?",
                (rs, n) -> new Details(rs.getString(1), rs.getObject(2, LocalDate.class)), plotId);
        List<HistoryRow> history = jdbc.query("""
                SELECT obs_date, ndvi_median, pixel_count, cloud_fraction FROM satellite_observations
                WHERE walk_id = ? ORDER BY obs_date
                """, (rs, n) -> new HistoryRow(rs.getObject(1, LocalDate.class), rs.getBigDecimal(2), rs.getInt(3),
                rs.getBigDecimal(4)), walkId);
        // An empty minimum is sent as empty, so the season check answers NOT_ENOUGH_DATA rather than guessing.
        Integer minClearPixels = thresholds.value("min_clear_pixels").map(BigDecimal::intValue).orElse(null);
        if ("SAMPLE".equals(source)) {
            return new MonitorRequest(plotId, "SAMPLE", null, null, from, null, minClearPixels, history);
        }
        return new MonitorRequest(plotId, "GEE", readJson(plot.geojson()), plot.planting(), from, to,
                minClearPixels, history);
    }

    private void save(long jobId, long plotId, long walkId, JsonNode result) {
        String origin = result.path("dataOrigin").asText();
        String version = result.path("modelVersion").asText();
        for (JsonNode row : result.path("observations")) {
            jdbc.update("""
                    INSERT INTO satellite_observations (plot_id, walk_id, obs_date, source, data_origin, ndvi_median,
                                                        pixel_count, cloud_fraction, scene_cloud_pct, job_id, model_version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE data_origin = VALUES(data_origin), ndvi_median = VALUES(ndvi_median),
                        pixel_count = VALUES(pixel_count), cloud_fraction = VALUES(cloud_fraction),
                        scene_cloud_pct = VALUES(scene_cloud_pct), job_id = VALUES(job_id),
                        model_version = VALUES(model_version), fetched_at = CURRENT_TIMESTAMP(6)
                    """, plotId, walkId, date(row, "date"), row.path("source").asText("S2"), origin,
                    decimal(row, "ndviMedian"), row.path("pixelCount").asInt(), decimal(row, "cloudFraction"),
                    decimal(row, "sceneCloudPct"), jobId, version);
        }
        for (JsonNode row : result.path("rainfall")) {
            if (row.path("rainMm").isNull() || row.path("rainMm").isMissingNode()) {
                continue;
            }
            jdbc.update("""
                    INSERT INTO satellite_rainfall (plot_id, walk_id, obs_date, source, data_origin, rain_mm, job_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE rain_mm = VALUES(rain_mm), data_origin = VALUES(data_origin),
                        job_id = VALUES(job_id), fetched_at = CURRENT_TIMESTAMP(6)
                    """, plotId, walkId, date(row, "date"), row.path("source").asText("CHIRPS"), origin,
                    decimal(row, "rainMm"), jobId);
        }

        JsonNode check = result.path("seasonCheck");
        String flag = check.path("flag").asText();
        ObjectNode explanation = mapper.createObjectNode();
        explanation.set("signals", check.path("signals"));
        explanation.put("dataQualityNote", check.path("dataQualityNote").asText());
        jdbc.update("""
                INSERT INTO season_checks (plot_id, walk_id, job_id, check_date, planting_date, flag, reason, confidence,
                                           images_used, pixels_used, latest_image_date, explanation_json, params_json,
                                           data_origin, model_version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, plotId, walkId, jobId, date(check, "checkDate"), date(result, "plantingDate"), flag,
                check.path("reason").asText(), check.path("confidence").asText("LOW"),
                check.path("imagesUsed").asInt(), check.path("pixelsUsed").isNumber() ? check.get("pixelsUsed").asInt() : null,
                date(check, "latestImageDate"),
                json(explanation), json(result.path("params")), origin, version);

        jdbc.update("""
                UPDATE satellite_jobs SET status = 'DONE', images_found = ?, fetch_from = ?, fetch_to = ?, data_origin = ?,
                    model_version = ?, error_code = NULL, error = NULL, finished_at = CURRENT_TIMESTAMP(6)
                WHERE id = ?
                """, result.path("observations").size(), date(result, "fetchFrom"), date(result, "fetchTo"), origin,
                version, jobId);
        recordModelVersion(version);

        if ("INCONSISTENT".equals(flag) && walkIsCurrent(walkId)) {
            boolean opened = flags.raise(plotId, FlagType.SATELLITE_INCONSISTENT, "The satellite images since "
                    + "planting don't look like a maize season (" + check.path("reason").asText() + ", latest image "
                    + check.path("latestImageDate").asText("none") + "). This is a review flag, not proof: check the "
                    + "crop and the planting date at the plot.", null);
            if (opened) {
                plots.findById(plotId).ifPresent(plot -> {
                    rules.refresh(plot);
                    plots.save(plot);
                });
            }
        }
    }

    /** The first result from a new season-check version is audited, like the status model's versions. */
    private void recordModelVersion(String version) {
        int added = jdbc.update("INSERT IGNORE INTO model_versions (name, version) VALUES (?, ?)", MODEL_NAME, version);
        if (added == 1) {
            jdbc.update("""
                    INSERT INTO audit_log (action, entity, after_json)
                    VALUES ('MODEL_VERSION_ADDED', 'model_versions', JSON_OBJECT('name', ?, 'version', ?))
                    """, MODEL_NAME, version);
        }
    }

    private boolean walkIsCurrent(long walkId) {
        Integer current = jdbc.queryForObject(
                "SELECT COUNT(*) FROM gps_walks WHERE id = ? AND status = 'CURRENT'", Integer.class, walkId);
        return current != null && current > 0;
    }

    // ===== Restarts and threads =====

    /** A restart stops the worker. Jobs it left behind are marked INTERRUPTED; the next refresh queues them again. */
    @EventListener(ApplicationReadyEvent.class)
    public void markInterruptedJobs() {
        int interrupted = jdbc.update("""
                UPDATE satellite_jobs SET status = 'INTERRUPTED', error_code = 'INTERRUPTED',
                    error = 'The backend restarted before this fetch finished.', finished_at = CURRENT_TIMESTAMP(6)
                WHERE status IN ('QUEUED', 'RUNNING')
                """);
        if (interrupted > 0) {
            log.warn("{} satellite job(s) were interrupted by a restart", interrupted);
        }
    }

    /** Always on the worker thread, so a job never runs inside the transaction that queued it. */
    private void run(Runnable work) {
        Future<?> future = worker.submit(() -> {
            try {
                work.run();
            } catch (RuntimeException ex) {
                log.error("Satellite monitoring failed", ex);
            }
        });
        if (config.runInline()) {
            try {
                future.get();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException ex) {
                throw new IllegalStateException(ex.getCause());
            }
        }
    }

    @PreDestroy
    void stopWorker() {
        worker.shutdownNow();
    }

    // ===== Helpers =====

    private long insertJob(long plotId, long walkId, String kind, String source, String trigger, LocalDate from,
                           LocalDate to) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO satellite_jobs (plot_id, walk_id, kind, source, trigger_type, fetch_from, fetch_to)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, plotId);
            ps.setLong(2, walkId);
            ps.setString(3, kind);
            ps.setString(4, source);
            ps.setString(5, trigger);
            ps.setDate(6, from == null ? null : Date.valueOf(from));
            ps.setDate(7, to == null ? null : Date.valueOf(to));
            return ps;
        }, keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }

    private static LocalDate date(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? LocalDate.parse(value.asText()) : null;
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.decimalValue() : null;
    }

    private String json(JsonNode node) {
        try {
            return mapper.writeValueAsString(node);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private JsonNode readJson(String json) {
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
