package com.rinda.backend.claim;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.rinda.backend.plot.VerificationLevel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The parametric claim trigger (spec §3.7). It runs after every saved ML result and is the only code that
 * creates claims. It never decides a claim: every claim starts UNDER_REVIEW and a person decides it.
 *
 * Rule: an ACTIVE plot with a COVERED enrollment gets a claim when it has been AT_RISK for at least X days
 * without a break inside the sensitive stage, with enough valid observations, AND either the whole harvest
 * forecast range is below Y% of normal OR the rain deficit is at least Z%.
 *
 * X, Y, Z and the stage come from the thresholds table. If any of them is missing, nothing triggers:
 * an empty value means "not supplied yet", never zero. Missing data never counts as loss.
 */
@Service
public class ClaimTriggerService {

    private static final Logger log = LoggerFactory.getLogger(ClaimTriggerService.class);

    static final String AT_RISK_DAYS = "trigger_at_risk_days";
    static final String FORECAST_PCT = "trigger_forecast_pct";
    static final String RAIN_DEFICIT_PCT = "trigger_rain_deficit_pct";
    static final String STAGE_START = "sensitive_stage_start_dap";
    static final String STAGE_END = "sensitive_stage_end_dap";
    static final String MIN_VALID_OBS = "trigger_min_valid_obs";
    static final String MIN_CLEAR_PIXELS = "min_clear_pixels";
    private static final List<String> REQUIRED = List.of(AT_RISK_DAYS, FORECAST_PCT, RAIN_DEFICIT_PCT, STAGE_START,
            STAGE_END, MIN_VALID_OBS, MIN_CLEAR_PIXELS);

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public ClaimTriggerService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    /** Why a plot did or did not get a claim. Returned for tests and logs; nothing else depends on it. */
    public enum Outcome {
        CLAIM_CREATED, PLOT_NOT_ACTIVE, NOT_COVERED, CLAIM_EXISTS, RULE_NOT_CONFIGURED, NO_PLANTING_DATE,
        NOT_AT_RISK_IN_STAGE, AT_RISK_TOO_SHORT, TOO_FEW_VALID_OBSERVATIONS, NO_LOSS_SIGNAL
    }

    private record PlotRow(long farmerId, String name, String status, LocalDate plantingDate, boolean demo,
                           String verificationLevel, String upi) {
    }

    private record Cover(long enrollmentId, long seasonId, String seasonCode) {
    }

    private record Threshold(BigDecimal value, String status) {
    }

    record StatusRow(LocalDate date, String status, BigDecimal anomaly, BigDecimal rainPct, String cause,
                     String confidence, String explanationJson, String modelVersion, String dataOrigin) {
    }

    private record ForecastRow(LocalDate date, BigDecimal lowPct, BigDecimal highPct, String confidence,
                               String modelVersion, String dataOrigin) {
    }

    @Transactional
    public Outcome evaluate(long plotId) {
        PlotRow plot = loadPlot(plotId);
        // Claims only on plots a person checked in the field, and never while disputed (decision A, 2026-11-11).
        if (plot == null || !"ACTIVE".equals(plot.status())
                || !VerificationLevel.valueOf(plot.verificationLevel()).atLeast(VerificationLevel.FIELD_VERIFIED)) {
            return Outcome.PLOT_NOT_ACTIVE;
        }

        Map<String, Threshold> thresholds = loadThresholds();
        if (REQUIRED.stream().anyMatch(key -> thresholds.get(key) == null || thresholds.get(key).value() == null)) {
            log.debug("Claim trigger for plot {} skipped: trigger thresholds are not supplied yet", plotId);
            return Outcome.RULE_NOT_CONFIGURED;
        }
        if (plot.plantingDate() == null) {
            return Outcome.NO_PLANTING_DATE;
        }

        int stageStart = thresholds.get(STAGE_START).value().intValue();
        int stageEnd = thresholds.get(STAGE_END).value().intValue();
        List<StatusRow> inStage = statusesInStage(plotId, plot.plantingDate(), stageStart, stageEnd);
        List<StatusRow> run = currentAtRiskRun(inStage);
        if (run.isEmpty()) {
            return Outcome.NOT_AT_RISK_IN_STAGE;
        }

        LocalDate runStart = run.get(0).date();
        LocalDate runEnd = run.get(run.size() - 1).date();
        Optional<Cover> cover = coverOn(plotId, runEnd);
        if (cover.isEmpty()) {
            return Outcome.NOT_COVERED;
        }
        if (claimExists(plotId, plot.upi(), cover.get().seasonId())) {
            return Outcome.CLAIM_EXISTS;
        }

        long runDays = ChronoUnit.DAYS.between(runStart, runEnd);
        if (BigDecimal.valueOf(runDays).compareTo(thresholds.get(AT_RISK_DAYS).value()) < 0) {
            return Outcome.AT_RISK_TOO_SHORT;
        }
        int validObservations = validObservations(plotId, runStart, runEnd,
                thresholds.get(MIN_CLEAR_PIXELS).value().intValue());
        if (validObservations < thresholds.get(MIN_VALID_OBS).value().intValue()) {
            return Outcome.TOO_FEW_VALID_OBSERVATIONS;
        }

        // Yield: the WHOLE forecast range is below Y. Rain: the deficit (100 - rain % of normal) is at least Z.
        Optional<ForecastRow> forecast = latestForecastRange(plotId, runEnd);
        boolean yieldMet = forecast.isPresent()
                && forecast.get().highPct().compareTo(thresholds.get(FORECAST_PCT).value()) < 0;
        BigDecimal rainPct = run.get(run.size() - 1).rainPct();
        BigDecimal rainDeficit = rainPct == null ? null : BigDecimal.valueOf(100).subtract(rainPct);
        boolean rainMet = rainDeficit != null && rainDeficit.compareTo(thresholds.get(RAIN_DEFICIT_PCT).value()) >= 0;
        if (!yieldMet && !rainMet) {
            return Outcome.NO_LOSS_SIGNAL;
        }

        ObjectNode evidence = evidence(plotId, plot, cover.get(), thresholds, run, runDays, validObservations,
                forecast, yieldMet, rainPct, rainDeficit, rainMet, stageStart, stageEnd);
        boolean demo = plot.demo() || run.stream().anyMatch(s -> "SAMPLE_FILE".equals(s.dataOrigin()));
        String modelVersion = run.get(run.size() - 1).modelVersion();
        long claimId = insertClaim(plotId, cover.get().seasonId(), runEnd, evidence, modelVersion, demo);

        jdbc.update("""
                INSERT INTO audit_log (action, entity, entity_id, after_json)
                VALUES ('CLAIM_TRIGGERED', 'claims', ?, JSON_OBJECT('plotId', ?, 'seasonId', ?, 'triggerDate', ?,
                        'status', 'UNDER_REVIEW', 'modelVersion', ?))
                """, claimId, plotId, cover.get().seasonId(), runEnd.toString(), modelVersion);
        notifyFarmer(plot, plotId, claimId, demo);
        log.info("Claim {} triggered for plot {} (season {})", claimId, plotId, cover.get().seasonCode());
        return Outcome.CLAIM_CREATED;
    }

    // ===== Reads =====

    private PlotRow loadPlot(long plotId) {
        // Seeded demo plots have no planting date of their own; their latest run's date is used, as for the charts.
        return jdbc.query("""
                SELECT p.farmer_id, p.name, p.status,
                       COALESCE(p.planting_date,
                                (SELECT r.planting_date FROM ml_runs r WHERE r.plot_id = p.id ORDER BY r.id DESC LIMIT 1)),
                       p.is_demo, p.verification_level, p.upi
                FROM plots p WHERE p.id = ?
                """, (rs, n) -> new PlotRow(rs.getLong(1), rs.getString(2), rs.getString(3),
                rs.getObject(4, LocalDate.class), rs.getBoolean(5), rs.getString(6), rs.getString(7)), plotId)
                .stream().findFirst().orElse(null);
    }

    private Map<String, Threshold> loadThresholds() {
        Map<String, Threshold> values = new LinkedHashMap<>();
        jdbc.query("SELECT threshold_key, threshold_value, status FROM thresholds", rs -> {
            values.put(rs.getString(1), new Threshold(rs.getBigDecimal(2), rs.getString(3)));
        });
        return values;
    }

    /** Statuses whose date falls in the sensitive stage, oldest first. A re-run for the same date replaces its row. */
    private List<StatusRow> statusesInStage(long plotId, LocalDate planting, int startDap, int endDap) {
        return jdbc.query("""
                SELECT status_date, status, anomaly, rain_pct, cause, confidence, explanation_json, model_version,
                       data_origin
                FROM plot_status
                WHERE plot_id = ? AND status_date BETWEEN ? AND ?
                ORDER BY status_date, id
                """, (rs, n) -> new StatusRow(rs.getObject(1, LocalDate.class), rs.getString(2), rs.getBigDecimal(3),
                rs.getBigDecimal(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8),
                rs.getString(9)), plotId, Date.valueOf(planting.plusDays(startDap)),
                Date.valueOf(planting.plusDays(endDap)));
    }

    /**
     * The unbroken run of AT_RISK statuses that ends with the newest status in the stage.
     * Any other status, including NOT_ENOUGH_DATA, breaks the run: missing data is never counted as loss.
     */
    static List<StatusRow> currentAtRiskRun(List<StatusRow> inStage) {
        List<StatusRow> run = new ArrayList<>();
        for (int i = inStage.size() - 1; i >= 0 && "AT_RISK".equals(inStage.get(i).status()); i--) {
            run.add(0, inStage.get(i));
        }
        return run;
    }

    private Optional<Cover> coverOn(long plotId, LocalDate date) {
        return jdbc.query("""
                SELECT e.id, e.season_id, s.code FROM enrollments e
                JOIN seasons s ON s.id = e.season_id
                WHERE e.plot_id = ? AND e.status = 'COVERED'
                  AND (e.start_date IS NULL OR e.start_date <= ?)
                  AND (e.end_date IS NULL OR e.end_date >= ?)
                ORDER BY e.id DESC
                LIMIT 1
                """, (rs, n) -> new Cover(rs.getLong(1), rs.getLong(2), rs.getString(3)), plotId,
                Date.valueOf(date), Date.valueOf(date)).stream().findFirst();
    }

    /** One claim per plot per season, and one per UPI per season (a UPI can't be insured twice). */
    private boolean claimExists(long plotId, String upi, long seasonId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM claims c JOIN plots p ON p.id = c.plot_id
                WHERE c.season_id = ? AND (c.plot_id = ? OR (? IS NOT NULL AND p.upi = ?))
                """, Integer.class, seasonId, plotId, upi, upi);
        return count != null && count > 0;
    }

    /** Sentinel-2 dates in the run with a reading and enough clear pixels (question 15). */
    private int validObservations(long plotId, LocalDate from, LocalDate to, int minClearPixels) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM observations
                WHERE plot_id = ? AND source = 'S2' AND ndvi IS NOT NULL AND clear_pixels >= ?
                  AND obs_date BETWEEN ? AND ?
                """, Integer.class, plotId, minClearPixels, Date.valueOf(from), Date.valueOf(to));
        return count == null ? 0 : count;
    }

    /** The newest forecast up to the trigger date that is a real range (not a "too early" reason). */
    private Optional<ForecastRow> latestForecastRange(long plotId, LocalDate upTo) {
        return jdbc.query("""
                SELECT forecast_date, low_pct, high_pct, confidence, model_version, data_origin
                FROM harvest_forecasts
                WHERE plot_id = ? AND forecast_date <= ? AND low_pct IS NOT NULL AND high_pct IS NOT NULL
                ORDER BY forecast_date DESC, id DESC
                LIMIT 1
                """, (rs, n) -> new ForecastRow(rs.getObject(1, LocalDate.class), rs.getBigDecimal(2),
                rs.getBigDecimal(3), rs.getString(4), rs.getString(5), rs.getString(6)), plotId, Date.valueOf(upTo))
                .stream().findFirst();
    }

    // ===== Evidence snapshot (frozen with the claim, spec §3.7) =====

    private ObjectNode evidence(long plotId, PlotRow plot, Cover cover, Map<String, Threshold> thresholds,
                                List<StatusRow> run, long runDays, int validObservations,
                                Optional<ForecastRow> forecast, boolean yieldMet, BigDecimal rainPct,
                                BigDecimal rainDeficit, boolean rainMet, int stageStart, int stageEnd) {
        ObjectNode e = mapper.createObjectNode();
        LocalDate runStart = run.get(0).date();
        LocalDate runEnd = run.get(run.size() - 1).date();

        e.put("rule", "AT_RISK for at least " + plain(thresholds.get(AT_RISK_DAYS).value())
                + " days without a break between days " + stageStart + " and " + stageEnd
                + " after planting, and the whole harvest forecast range below "
                + plain(thresholds.get(FORECAST_PCT).value()) + "% of normal or a rain deficit of at least "
                + plain(thresholds.get(RAIN_DEFICIT_PCT).value()) + "%.");
        e.put("triggerReason", reason(runDays, yieldMet, forecast, rainMet, rainDeficit));
        e.put("plotId", plotId);
        e.put("plotName", plot.name());
        e.put("seasonCode", cover.seasonCode());
        e.put("plantingDate", plot.plantingDate().toString());

        ObjectNode used = e.putObject("thresholds");
        for (String key : REQUIRED) {
            ObjectNode t = used.putObject(key);
            t.put("value", thresholds.get(key).value());
            t.put("status", thresholds.get(key).status());
        }
        ObjectNode stage = e.putObject("sensitiveStage");
        stage.put("startDap", stageStart);
        stage.put("endDap", stageEnd);

        ObjectNode atRisk = e.putObject("atRiskRun");
        atRisk.put("from", runStart.toString());
        atRisk.put("to", runEnd.toString());
        atRisk.put("days", runDays);
        atRisk.put("fromDap", ChronoUnit.DAYS.between(plot.plantingDate(), runStart));
        atRisk.put("toDap", ChronoUnit.DAYS.between(plot.plantingDate(), runEnd));
        atRisk.put("validObservations", validObservations);

        ArrayNode statuses = e.putArray("statuses");
        BigDecimal lowestAnomaly = null;
        Set<String> models = new LinkedHashSet<>();
        Set<String> flags = new LinkedHashSet<>();
        for (StatusRow s : run) {
            ObjectNode row = statuses.addObject();
            row.put("date", s.date().toString());
            row.put("dap", ChronoUnit.DAYS.between(plot.plantingDate(), s.date()));
            row.put("status", s.status());
            row.put("anomaly", s.anomaly());
            row.put("rainPct", s.rainPct());
            row.put("cause", s.cause());
            row.put("confidence", s.confidence());
            row.put("modelVersion", s.modelVersion());
            row.put("dataOrigin", s.dataOrigin());
            JsonNode explanation = read(s.explanationJson());
            row.set("signals", explanation.path("signals"));
            models.add(s.modelVersion());
            if (s.anomaly() != null && (lowestAnomaly == null || s.anomaly().compareTo(lowestAnomaly) < 0)) {
                lowestAnomaly = s.anomaly();
            }
            if ("LOW".equals(s.confidence())) {
                flags.add("LOW_CONFIDENCE_STATUS");
            }
            if ("UNEXPLAINED".equals(s.cause()) || explanation.path("needsFieldVisit").asBoolean(false)) {
                flags.add("FIELD_VISIT_SUGGESTED");
            }
            for (JsonNode signal : explanation.path("signals")) {
                if ("NORMAL_CURVE_ESTIMATED".equals(signal.path("code").asText())) {
                    flags.add("NORMAL_CURVE_ESTIMATED"); // question 19: a person checks these
                }
            }
            if ("SAMPLE_FILE".equals(s.dataOrigin())) {
                flags.add("SAMPLE_DATA");
            }
        }
        e.put("lowestAnomaly", lowestAnomaly);

        ObjectNode f = e.putObject("forecast");
        f.put("met", yieldMet);
        forecast.ifPresent(row -> {
            f.put("date", row.date().toString());
            f.put("lowPct", row.lowPct());
            f.put("highPct", row.highPct());
            f.put("confidence", row.confidence());
            f.put("modelVersion", row.modelVersion());
            f.put("dataOrigin", row.dataOrigin());
            models.add(row.modelVersion());
            if ("LOW".equals(row.confidence())) {
                flags.add("LOW_CONFIDENCE_FORECAST");
            }
        });
        ObjectNode rain = e.putObject("rain");
        rain.put("met", rainMet);
        rain.put("rainPct", rainPct);
        rain.put("deficitPct", rainDeficit);
        if (rainDeficit != null) {
            e.put("rainDeficitPct", rainDeficit.stripTrailingZeros().toPlainString());
        }

        ArrayNode modelList = e.putArray("modelVersions");
        models.stream().filter(Objects::nonNull).forEach(modelList::add);
        ArrayNode flagList = e.putArray("reviewFlags");
        flags.forEach(flagList::add);
        e.set("chart", chartData(plotId, plot.plantingDate(), runEnd));
        return e;
    }

    private String reason(long runDays, boolean yieldMet, Optional<ForecastRow> forecast, boolean rainMet,
                          BigDecimal rainDeficit) {
        StringBuilder sb = new StringBuilder("The crop was well below its normal growth for " + runDays
                + " days in the flowering and grain-filling stage");
        List<String> parts = new ArrayList<>();
        if (yieldMet && forecast.isPresent()) {
            parts.add("the harvest forecast is " + plain(forecast.get().lowPct()) + "–"
                    + plain(forecast.get().highPct()) + "% of normal");
        }
        if (rainMet && rainDeficit != null) {
            parts.add("rain was " + plain(rainDeficit) + "% below normal");
        }
        if (!parts.isEmpty()) {
            sb.append(", and ").append(String.join(" and ", parts));
        }
        return sb.append('.').toString();
    }

    /** NDVI, the normal band and rain from planting to the trigger date, so the officer sees what the farmer saw. */
    private ObjectNode chartData(long plotId, LocalDate planting, LocalDate upTo) {
        ObjectNode chart = mapper.createObjectNode();
        ArrayNode obs = chart.putArray("observations");
        jdbc.query("""
                SELECT obs_date, ndvi, smoothed_ndvi, clear_pixels FROM observations
                WHERE plot_id = ? AND source = 'S2' AND obs_date BETWEEN ? AND ?
                ORDER BY obs_date
                """, rs -> {
            ObjectNode row = obs.addObject();
            LocalDate date = rs.getObject(1, LocalDate.class);
            row.put("date", date.toString());
            row.put("dap", ChronoUnit.DAYS.between(planting, date));
            row.put("ndvi", rs.getBigDecimal(2));
            row.put("smoothedNdvi", rs.getBigDecimal(3));
            row.put("clearPixels", rs.getObject(4, Integer.class));
        }, plotId, Date.valueOf(planting), Date.valueOf(upTo));

        ArrayNode band = chart.putArray("baseline");
        jdbc.query("""
                SELECT b.dap_bin, b.p10, b.p50, b.p90, b.is_filled, b.version FROM baselines b
                JOIN (SELECT season_type, version FROM baselines WHERE plot_id = ? ORDER BY id DESC LIMIT 1) latest
                  ON latest.season_type = b.season_type AND latest.version = b.version
                WHERE b.plot_id = ?
                ORDER BY b.dap_bin
                """, rs -> {
            ObjectNode row = band.addObject();
            row.put("dapBin", rs.getInt(1));
            row.put("p10", rs.getBigDecimal(2));
            row.put("p50", rs.getBigDecimal(3));
            row.put("p90", rs.getBigDecimal(4));
            row.put("filled", rs.getBoolean(5));
            chart.put("baselineVersion", rs.getString(6));
        }, plotId, plotId);

        ArrayNode rain = chart.putArray("rainDaily");
        jdbc.query("""
                SELECT obs_date, rain_mm FROM weather_daily
                WHERE plot_id = ? AND obs_date BETWEEN ? AND ?
                ORDER BY obs_date
                """, rs -> {
            ObjectNode row = rain.addObject();
            row.put("date", rs.getObject(1, LocalDate.class).toString());
            row.put("rainMm", rs.getBigDecimal(2));
        }, plotId, Date.valueOf(planting), Date.valueOf(upTo));
        return chart;
    }

    // ===== Writes =====

    /** Payout rate and amount stay NULL until the insurer confirms the rates (question 4). */
    private long insertClaim(long plotId, long seasonId, LocalDate triggerDate, ObjectNode evidence,
                             String modelVersion, boolean demo) {
        String json = write(evidence);
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO claims (plot_id, season_id, trigger_date, status, evidence_snapshot_json,
                                        model_version, is_demo)
                    VALUES (?, ?, ?, 'UNDER_REVIEW', ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, plotId);
            ps.setLong(2, seasonId);
            ps.setDate(3, Date.valueOf(triggerDate));
            ps.setString(4, json);
            ps.setString(5, modelVersion);
            ps.setBoolean(6, demo);
            return ps;
        }, keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }

    private void notifyFarmer(PlotRow plot, long plotId, long claimId, boolean demo) {
        jdbc.update("""
                INSERT INTO notifications (user_id, type, code, title, body, link, plot_id, is_demo)
                VALUES (?, 'CLAIM', 'CLAIM_TRIGGERED', ?, ?, ?, ?, ?)
                """, plot.farmerId(), "A claim was started for your plot",
                "Satellite data shows a large loss at " + plot.name() + ". An insurance officer will check it. "
                        + "You don't need to fill in anything.", "/claims/" + claimId, plotId, demo);
    }

    private JsonNode read(String json) {
        try {
            return json == null ? mapper.createObjectNode() : mapper.readTree(json);
        } catch (JsonProcessingException ex) {
            return mapper.createObjectNode();
        }
    }

    private String write(JsonNode node) {
        try {
            return mapper.writeValueAsString(node);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
