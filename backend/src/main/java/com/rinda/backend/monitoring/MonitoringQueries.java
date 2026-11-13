package com.rinda.backend.monitoring;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rinda.backend.common.ThresholdService;
import com.rinda.backend.monitoring.MonitoringDtos.JobView;
import com.rinda.backend.monitoring.MonitoringDtos.MonitoringResponse;
import com.rinda.backend.monitoring.MonitoringDtos.MonitoringState;
import com.rinda.backend.monitoring.MonitoringDtos.MonitoringSummary;
import com.rinda.backend.monitoring.MonitoringDtos.ObservationView;
import com.rinda.backend.monitoring.MonitoringDtos.RainView;
import com.rinda.backend.monitoring.MonitoringDtos.SeasonCheckView;
import com.rinda.backend.plot.Plot;
import com.rinda.backend.plot.VerificationLevel;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** What the app and the insurer dashboard show about a plot's satellite monitoring. Reads only. */
@Component
public class MonitoringQueries {

    static final List<String> NOTES = List.of(
            "Satellite data is a review flag, never proof of crop: satellites can't tell maize from similar crops.",
            "Not live: Rinda checks for new images once a week, and cloudy weeks have none. "
                    + "The date of the latest image is always shown.");

    private final JdbcTemplate jdbc;
    private final ThresholdService thresholds;
    private final ObjectMapper mapper;

    public MonitoringQueries(JdbcTemplate jdbc, ThresholdService thresholds, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.thresholds = thresholds;
        this.mapper = mapper;
    }

    private record Context(Long walkId, Integer minClearPixels, Optional<JobView> latestJob, boolean hasDoneJob,
                           LocalDate latestImageDate) {
    }

    private Context context(long plotId) {
        Long walkId = jdbc.queryForList("SELECT id FROM gps_walks WHERE plot_id = ? AND status = 'CURRENT'",
                Long.class, plotId).stream().findFirst().orElse(null);
        Integer min = thresholds.value("min_clear_pixels").map(BigDecimal::intValue).orElse(null);
        if (walkId == null) {
            return new Context(null, min, Optional.empty(), false, null);
        }
        Optional<JobView> job = jdbc.query("""
                SELECT id, kind, status, fetch_from, fetch_to, error_code, created_at, finished_at
                FROM satellite_jobs WHERE walk_id = ? ORDER BY id DESC LIMIT 1
                """, (rs, n) -> {
            Timestamp finished = rs.getTimestamp(8);
            return new JobView(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getObject(4, LocalDate.class),
                    rs.getObject(5, LocalDate.class), rs.getString(6), rs.getTimestamp(7).toInstant(),
                    finished == null ? null : finished.toInstant());
        }, walkId).stream().findFirst();
        Integer done = jdbc.queryForObject(
                "SELECT COUNT(*) FROM satellite_jobs WHERE walk_id = ? AND status = 'DONE'", Integer.class, walkId);
        // An empty minimum means no image is counted as usable: never treat it as zero.
        LocalDate latest = min == null ? null : jdbc.queryForObject("""
                SELECT MAX(obs_date) FROM satellite_observations
                WHERE walk_id = ? AND ndvi_median IS NOT NULL AND pixel_count >= ?
                """, LocalDate.class, walkId, min);
        return new Context(walkId, min, job, done != null && done > 0, latest);
    }

    private static MonitoringState state(Context c) {
        if (c.walkId() == null) {
            return MonitoringState.NO_BOUNDARY;
        }
        if (!c.hasDoneJob()) {
            boolean failed = c.latestJob().map(j -> List.of("FAILED", "INTERRUPTED").contains(j.status()))
                    .orElse(false);
            return failed ? MonitoringState.FETCH_FAILED : MonitoringState.FETCHING;
        }
        return c.latestImageDate() == null ? MonitoringState.NO_CLEAR_IMAGE : MonitoringState.DATA_AVAILABLE;
    }

    public MonitoringSummary summary(long plotId) {
        Context c = context(plotId);
        String flag = c.walkId() == null ? null : latestCheck(c.walkId()).map(SeasonCheckView::flag).orElse(null);
        return new MonitoringSummary(state(c), c.latestImageDate(), flag);
    }

    public MonitoringResponse view(Plot plot) {
        long plotId = plot.getId();
        Context c = context(plotId);
        MonitoringState state = state(c);
        boolean verified = plot.getVerificationLevel().atLeast(VerificationLevel.FIELD_VERIFIED);
        String verificationNote = verified ? null : "This plot is not verified yet (" + plot.getVerificationLevel()
                + "). The satellite curve checks what was declared; crop health and claims start after the "
                + "agent's field check.";

        List<ObservationView> observations = List.of();
        List<RainView> rainfall = List.of();
        Optional<SeasonCheckView> check = Optional.empty();
        String origin = null;
        LocalDate since = null;
        Instant lastFetched = null;
        if (c.walkId() != null) {
            Integer min = c.minClearPixels();
            observations = jdbc.query("""
                    SELECT obs_date, source, ndvi_median, pixel_count, cloud_fraction FROM satellite_observations
                    WHERE walk_id = ? ORDER BY obs_date
                    """, (rs, n) -> {
                BigDecimal ndvi = rs.getBigDecimal(3);
                int pixels = rs.getInt(4);
                return new ObservationView(rs.getObject(1, LocalDate.class), rs.getString(2), ndvi, pixels,
                        rs.getBigDecimal(5), ndvi != null && min != null && pixels >= min);
            }, c.walkId());
            rainfall = jdbc.query("""
                    SELECT obs_date, rain_mm, source FROM satellite_rainfall WHERE walk_id = ? ORDER BY obs_date
                    """, (rs, n) -> new RainView(rs.getObject(1, LocalDate.class), rs.getBigDecimal(2),
                    rs.getString(3)), c.walkId());
            check = latestCheck(c.walkId());
            record Fetched(LocalDate since, Timestamp at, String origin) {
            }
            Fetched fetched = jdbc.queryForObject("""
                    SELECT MIN(fetch_from), MAX(finished_at),
                           (SELECT data_origin FROM satellite_jobs WHERE walk_id = ? AND status = 'DONE'
                            ORDER BY id DESC LIMIT 1)
                    FROM satellite_jobs WHERE walk_id = ? AND status = 'DONE'
                    """, (rs, n) -> new Fetched(rs.getObject(1, LocalDate.class), rs.getTimestamp(2),
                    rs.getString(3)), c.walkId(), c.walkId());
            since = check.map(SeasonCheckView::plantingDate).orElse(fetched.since());
            lastFetched = fetched.at() == null ? null : fetched.at().toInstant();
            origin = fetched.origin();
        }
        boolean refreshing = c.latestJob().map(j -> List.of("QUEUED", "RUNNING").contains(j.status())).orElse(false);

        return new MonitoringResponse(plotId, state, message(state, since), plot.getVerificationLevel(),
                verificationNote, verified, plot.isDemo(), origin, c.latestImageDate(), since, lastFetched, refreshing,
                "WEEKLY", c.minClearPixels(), check.orElse(null), observations, rainfall, c.latestJob().orElse(null),
                NOTES);
    }

    private static String message(MonitoringState state, LocalDate since) {
        return switch (state) {
            case NO_BOUNDARY -> "Walk the edge of your plot to start satellite monitoring.";
            case FETCHING -> "Fetching satellite data.";
            case FETCH_FAILED -> "Satellite data could not be fetched yet. Rinda tries again at the next weekly update.";
            case NO_CLEAR_IMAGE -> "No clear image since " + (since == null ? "planting" : since)
                    + "; showing rainfall only.";
            case DATA_AVAILABLE -> "Satellite images up to the date shown. Not live: updated weekly.";
        };
    }

    private Optional<SeasonCheckView> latestCheck(long walkId) {
        return jdbc.query("""
                SELECT flag, reason, confidence, check_date, planting_date, explanation_json, images_used, pixels_used,
                       latest_image_date, model_version, data_origin
                FROM season_checks WHERE walk_id = ? ORDER BY id DESC LIMIT 1
                """, (rs, n) -> {
            JsonNode explanation = readJson(rs.getString(6));
            return new SeasonCheckView(rs.getString(1), rs.getString(2), rs.getString(3),
                    rs.getObject(4, LocalDate.class), rs.getObject(5, LocalDate.class), explanation.path("signals"),
                    explanation.path("dataQualityNote").asText(null), rs.getInt(7), rs.getObject(8, Integer.class),
                    rs.getObject(9, LocalDate.class), rs.getString(10), rs.getString(11));
        }, walkId).stream().findFirst();
    }

    private JsonNode readJson(String json) {
        try {
            return json == null ? mapper.createObjectNode() : mapper.readTree(json);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
