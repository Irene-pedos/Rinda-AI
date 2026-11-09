package com.rinda.backend.analysis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rinda.backend.analysis.AnalysisDtos.AnalysisItemResponse;
import com.rinda.backend.analysis.AnalysisDtos.AnalysisRunResponse;
import com.rinda.backend.analysis.MlClient.MlCallException;
import com.rinda.backend.analysis.MlClient.PlotJob;
import com.rinda.backend.analysis.MlClient.Processed;
import com.rinda.backend.common.ApiException;
import com.rinda.backend.common.PageResponse;
import com.rinda.backend.config.RindaProperties;
import jakarta.annotation.PreDestroy;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Run analysis" (spec §10.1): asks the ML service to analyse plots, one at a time. The ML service posts each
 * result back to /internal/ml/results, where it is saved and the claim trigger runs. Re-running is safe: saving
 * the same result again changes nothing, and the trigger never makes a second claim.
 *
 * Real data (GEE) only goes to verified, confirmed, real plots; the simulated sample only to demo plots, and only
 * when asked for. Nothing ever falls back to sample data when real data was asked for.
 */
@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    private final JdbcTemplate jdbc;
    private final MlClient ml;
    private final ObjectMapper mapper;
    private final RindaProperties.Analysis config;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "analysis-runner");
        t.setDaemon(true);
        return t;
    });

    public AnalysisService(JdbcTemplate jdbc, MlClient ml, ObjectMapper mapper, RindaProperties properties) {
        this.jdbc = jdbc;
        this.ml = ml;
        this.mapper = mapper;
        this.config = properties.analysis();
    }

    private record Eligibility(long plotId, String status, String dataStatus, boolean demo, LocalDate planting,
                               String seasonType) {
    }

    // ===== Start =====

    /** Records the run and its plots. Call {@link #launch(long)} after this transaction commits. */
    @Transactional
    public long start(Long actorId, String source, List<Long> plotIds, LocalDate asOf, String triggerType) {
        Integer active = jdbc.queryForObject(
                "SELECT COUNT(*) FROM analysis_runs WHERE status IN ('QUEUED', 'RUNNING')", Integer.class);
        if (active != null && active > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "ANALYSIS_ALREADY_RUNNING",
                    "An analysis run is already in progress. Wait for it to finish.");
        }
        if ("SAMPLE".equals(source) && (plotIds == null || plotIds.isEmpty())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PLOT_IDS_REQUIRED",
                    "Name the demo plots to fill with the simulated sample.");
        }

        List<Long> targets = plotIds == null || plotIds.isEmpty()
                ? jdbc.queryForList("""
                        SELECT id FROM plots
                        WHERE status = 'ACTIVE' AND data_status = 'CONFIRMED' AND is_demo = FALSE
                          AND planting_date IS NOT NULL
                        ORDER BY id
                        """, Long.class)
                : new ArrayList<>(new LinkedHashSet<>(plotIds));

        long runId = insertRun(source, triggerType, actorId, asOf);
        int total = 0;
        for (long plotId : targets) {
            Eligibility plot = eligibility(plotId);
            if (plot == null) {
                throw new ApiException(HttpStatus.NOT_FOUND, "PLOT_NOT_FOUND", "Plot " + plotId + " not found.");
            }
            String skip = skipReason(source, plot);
            jdbc.update("""
                    INSERT INTO analysis_run_items (run_id, plot_id, status, error_code, error, finished_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, runId, plotId, skip == null ? "QUEUED" : "SKIPPED", skip, skip == null ? null : explain(skip),
                    skip == null ? null : new Timestamp(System.currentTimeMillis()));
            total++;
        }
        jdbc.update("UPDATE analysis_runs SET plots_total = ? WHERE id = ?", total, runId);
        jdbc.update("""
                INSERT INTO audit_log (actor_id, action, entity, entity_id, after_json)
                VALUES (?, 'ANALYSIS_RUN_STARTED', 'analysis_runs', ?, JSON_OBJECT('source', ?, 'trigger', ?, 'plots', ?))
                """, actorId, runId, source, triggerType, total);
        return runId;
    }

    /** Runs in the background (or inline in tests). Must be called outside any transaction. */
    public void launch(long runId) {
        if (config.runInline()) {
            execute(runId);
        } else {
            worker.submit(() -> execute(runId));
        }
    }

    /** The optional 5-day schedule (spec §2.1). Off unless rinda.analysis.cron is set. */
    @Scheduled(cron = "${rinda.analysis.cron:-}", zone = "Africa/Kigali")
    public void scheduledRun() {
        try {
            long runId = start(null, "GEE", null, null, "SCHEDULE");
            launch(runId);
        } catch (ApiException ex) {
            log.info("Scheduled analysis skipped: {}", ex.getMessage());
        }
    }

    /** A restart stops the worker. Runs it left behind are marked INTERRUPTED, never left looking "RUNNING". */
    @EventListener(ApplicationReadyEvent.class)
    public void markInterruptedRuns() {
        jdbc.update("""
                UPDATE analysis_run_items i JOIN analysis_runs r ON r.id = i.run_id
                SET i.status = 'FAILED', i.error_code = 'INTERRUPTED',
                    i.error = 'The backend restarted before this plot was analysed.', i.finished_at = CURRENT_TIMESTAMP(6)
                WHERE r.status IN ('QUEUED', 'RUNNING') AND i.status IN ('QUEUED', 'RUNNING')
                """);
        int interrupted = jdbc.update("""
                UPDATE analysis_runs SET status = 'INTERRUPTED', finished_at = CURRENT_TIMESTAMP(6)
                WHERE status IN ('QUEUED', 'RUNNING')
                """);
        if (interrupted > 0) {
            log.warn("{} analysis run(s) were interrupted by a restart", interrupted);
        }
    }

    @PreDestroy
    void stopWorker() {
        worker.shutdownNow();
    }

    // ===== Execute =====

    void execute(long runId) {
        record RunRow(String source, LocalDate asOf) {
        }
        RunRow run = jdbc.queryForObject("SELECT source, as_of FROM analysis_runs WHERE id = ?",
                (rs, n) -> new RunRow(rs.getString(1), rs.getObject(2, LocalDate.class)), runId);
        jdbc.update("UPDATE analysis_runs SET status = 'RUNNING', started_at = CURRENT_TIMESTAMP(6) WHERE id = ?", runId);

        List<Long> queued = jdbc.queryForList(
                "SELECT plot_id FROM analysis_run_items WHERE run_id = ? AND status = 'QUEUED' ORDER BY id",
                Long.class, runId);
        int done = 0;
        int failed = 0;
        for (long plotId : queued) {
            if (analyse(runId, plotId, run.source(), run.asOf())) {
                done++;
            } else {
                failed++;
            }
            jdbc.update("UPDATE analysis_runs SET plots_done = ?, plots_failed = ? WHERE id = ?", done, failed, runId);
        }
        String status = failed > 0 && done == 0 ? "FAILED" : "DONE";
        jdbc.update("UPDATE analysis_runs SET status = ?, finished_at = CURRENT_TIMESTAMP(6) WHERE id = ?",
                status, runId);
        log.info("Analysis run {} finished: {} done, {} failed", runId, done, failed);
    }

    /** One plot, retried on ML-service or network failures. A refusal (4xx) is recorded and not retried. */
    private boolean analyse(long runId, long plotId, String source, LocalDate asOf) {
        jdbc.update("UPDATE analysis_run_items SET status = 'RUNNING' WHERE run_id = ? AND plot_id = ?", runId, plotId);
        MlCallException last = null;
        for (int attempt = 1; attempt <= config.maxAttempts(); attempt++) {
            jdbc.update("UPDATE analysis_run_items SET attempts = ? WHERE run_id = ? AND plot_id = ?",
                    attempt, runId, plotId);
            try {
                Processed processed = ml.process(job(plotId, source, asOf));
                jdbc.update("""
                        UPDATE analysis_run_items
                        SET status = 'DONE', ml_run_id = ?, error_code = NULL, error = NULL,
                            finished_at = CURRENT_TIMESTAMP(6)
                        WHERE run_id = ? AND plot_id = ?
                        """, processed.runId() > 0 ? processed.runId() : null, runId, plotId);
                return true;
            } catch (MlCallException ex) {
                last = ex;
                log.warn("Analysis of plot {} failed (attempt {}): {}", plotId, attempt, ex.getMessage());
                if (!ex.retryable()) {
                    break;
                }
            } catch (RuntimeException ex) {
                last = new MlCallException(500, ex.getMessage() == null ? ex.getClass().getSimpleName()
                        : ex.getMessage(), false);
                log.error("Analysis of plot {} failed unexpectedly", plotId, ex);
                break;
            }
        }
        String message = Objects.requireNonNullElse(last.getMessage(), "Analysis failed.");
        jdbc.update("""
                UPDATE analysis_run_items SET status = 'FAILED', error_code = ?, error = ?, finished_at = CURRENT_TIMESTAMP(6)
                WHERE run_id = ? AND plot_id = ?
                """, last.retryable() ? "ML_SERVICE_ERROR" : "REFUSED",
                message.length() > 500 ? message.substring(0, 500) : message, runId, plotId);
        return false;
    }

    private PlotJob job(long plotId, String source, LocalDate asOf) {
        if ("SAMPLE".equals(source)) {
            return new PlotJob(plotId, "SAMPLE", null, null, null, asOf);
        }
        record Details(String geojson, LocalDate planting) {
        }
        Details d = jdbc.queryForObject("SELECT geojson, planting_date FROM plots WHERE id = ?",
                (rs, n) -> new Details(rs.getString(1), rs.getObject(2, LocalDate.class)), plotId);
        Eligibility e = eligibility(plotId);
        return new PlotJob(plotId, "GEE", readJson(d.geojson()), d.planting(), e.seasonType(), asOf);
    }

    // ===== Eligibility =====

    private Eligibility eligibility(long plotId) {
        return jdbc.query("""
                SELECT p.id, p.status, p.data_status, p.is_demo, p.planting_date,
                       (SELECT s.type FROM enrollments e JOIN seasons s ON s.id = e.season_id
                        WHERE e.plot_id = p.id AND e.status IN ('COVERED', 'PENDING')
                        ORDER BY e.id DESC LIMIT 1)
                FROM plots p WHERE p.id = ?
                """, (rs, n) -> new Eligibility(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBoolean(4),
                rs.getObject(5, LocalDate.class), rs.getString(6)), plotId).stream().findFirst().orElse(null);
    }

    /** Why a plot can't be analysed with this source, or null when it can. */
    static String skipReason(String source, Eligibility plot) {
        if ("SAMPLE".equals(source)) {
            if (!plot.demo()) {
                return "REAL_PLOT";
            }
            // Monitoring starts after verification (spec §2.2 #4), for demo plots too.
            return "ACTIVE".equals(plot.status()) ? null : "PLOT_NOT_ACTIVE";
        }
        if (plot.demo()) {
            return "DEMO_PLOT";
        }
        if (!"ACTIVE".equals(plot.status())) {
            return "PLOT_NOT_ACTIVE";
        }
        if (!"CONFIRMED".equals(plot.dataStatus())) {
            return "PLOT_NOT_CONFIRMED";
        }
        if (plot.planting() == null) {
            return "NO_PLANTING_DATE";
        }
        if (plot.seasonType() == null) {
            return "NO_SEASON";
        }
        return null;
    }

    private static String explain(String code) {
        return switch (code) {
            case "REAL_PLOT" -> "The simulated sample can only go on demo plots.";
            case "DEMO_PLOT" -> "Demo plots are placeholders: real satellite data is not stored on them.";
            case "PLOT_NOT_ACTIVE" -> "Only verified plots are analysed.";
            case "PLOT_NOT_CONFIRMED" -> "The plot's location and planting date are not confirmed.";
            case "NO_PLANTING_DATE" -> "The plot has no planting date.";
            case "NO_SEASON" -> "The plot has no season enrollment, so the season type (A or B) is unknown.";
            default -> code;
        };
    }

    // ===== Read =====

    @Transactional(readOnly = true)
    public PageResponse<AnalysisRunResponse> list(int page, int size) {
        PageResponse.check(page, size);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM analysis_runs", Long.class);
        List<AnalysisRunResponse> items = jdbc.query(RUN_SELECT + " ORDER BY r.id DESC LIMIT ? OFFSET ?",
                (rs, n) -> mapRun(rs, null), size, (long) page * size);
        return PageResponse.of(items, page, size, total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public AnalysisRunResponse get(long runId) {
        List<AnalysisItemResponse> items = jdbc.query("""
                SELECT i.plot_id, p.name, i.status, i.attempts, i.error_code, i.error, i.ml_run_id, i.finished_at
                FROM analysis_run_items i JOIN plots p ON p.id = i.plot_id
                WHERE i.run_id = ? ORDER BY i.id
                """, (rs, n) -> {
            Timestamp finished = rs.getTimestamp(8);
            return new AnalysisItemResponse(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getInt(4),
                    rs.getString(5), rs.getString(6), rs.getObject(7, Long.class),
                    finished == null ? null : finished.toInstant());
        }, runId);
        return jdbc.query(RUN_SELECT + " WHERE r.id = ?", (rs, n) -> mapRun(rs, items), runId).stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ANALYSIS_RUN_NOT_FOUND", "Run not found."));
    }

    private static final String RUN_SELECT = """
            SELECT r.id, r.source, r.status, r.trigger_type, r.requested_by, r.as_of, r.plots_total, r.plots_done,
                   r.plots_failed,
                   (SELECT COUNT(*) FROM analysis_run_items i WHERE i.run_id = r.id AND i.status = 'SKIPPED'),
                   r.created_at, r.started_at, r.finished_at
            FROM analysis_runs r
            """;

    private static AnalysisRunResponse mapRun(ResultSet rs, List<AnalysisItemResponse> items) throws SQLException {
        Timestamp started = rs.getTimestamp(12);
        Timestamp finished = rs.getTimestamp(13);
        return new AnalysisRunResponse(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getObject(5, Long.class), rs.getObject(6, LocalDate.class), rs.getInt(7), rs.getInt(8),
                rs.getInt(9), rs.getInt(10), rs.getTimestamp(11).toInstant(),
                started == null ? null : started.toInstant(), finished == null ? null : finished.toInstant(), items);
    }

    // ===== Helpers =====

    private long insertRun(String source, String triggerType, Long actorId, LocalDate asOf) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO analysis_runs (source, trigger_type, requested_by, as_of) VALUES (?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, source);
            ps.setString(2, triggerType);
            ps.setObject(3, actorId);
            ps.setDate(4, asOf == null ? null : Date.valueOf(asOf));
            return ps;
        }, keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }

    private JsonNode readJson(String json) {
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
