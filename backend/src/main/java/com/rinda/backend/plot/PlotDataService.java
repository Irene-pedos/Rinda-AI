package com.rinda.backend.plot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.rinda.backend.auth.AuthPrincipal;
import com.rinda.backend.common.ApiException;
import com.rinda.backend.ml.MlDtos.DataOrigin;
import com.rinda.backend.plot.PlotDataDtos.BaselineBinResponse;
import com.rinda.backend.plot.PlotDataDtos.BaselineResponse;
import com.rinda.backend.plot.PlotDataDtos.EnrollmentResponse;
import com.rinda.backend.plot.PlotDataDtos.ForecastResponse;
import com.rinda.backend.plot.PlotDataDtos.ObservationResponse;
import com.rinda.backend.plot.PlotDataDtos.StatusResponse;
import com.rinda.backend.plot.PlotDataDtos.WeatherResponse;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads satellite and rain results for the plot charts. Visibility follows the plot itself. */
@Service
public class PlotDataService {

    private static final LocalDate EARLIEST = LocalDate.of(1000, 1, 1);
    private static final LocalDate LATEST = LocalDate.of(9999, 12, 31);
    private static final String NOT_ENOUGH_DATA = "NOT_ENOUGH_DATA";

    private final PlotService plots;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public PlotDataService(PlotService plots, JdbcTemplate jdbc, ObjectMapper mapper) {
        this.plots = plots;
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    private record BaselineSet(String seasonType, String version, DataOrigin origin) {
    }

    private record LastKnown(String status, LocalDate date) {
    }

    @Transactional(readOnly = true)
    public List<ObservationResponse> observations(AuthPrincipal principal, long plotId, LocalDate from, LocalDate to) {
        LocalDate planting = plantingDate(principal, plotId);
        checkRange(from, to);
        return jdbc.query("""
                SELECT obs_date, source, ndvi, smoothed_ndvi, clear_pixels, cloud_pct, data_origin
                FROM observations
                WHERE plot_id = ? AND obs_date BETWEEN ? AND ?
                ORDER BY obs_date, source
                """, (rs, n) -> {
            LocalDate date = rs.getObject(1, LocalDate.class);
            return new ObservationResponse(date, dap(date, planting), rs.getString(2), rs.getBigDecimal(3),
                    rs.getBigDecimal(4), rs.getObject(5, Integer.class), rs.getBigDecimal(6),
                    DataOrigin.valueOf(rs.getString(7)));
        }, plotId, orEarliest(from), orLatest(to));
    }

    @Transactional(readOnly = true)
    public List<WeatherResponse> weather(AuthPrincipal principal, long plotId, LocalDate from, LocalDate to) {
        LocalDate planting = plantingDate(principal, plotId);
        checkRange(from, to);
        return jdbc.query("""
                SELECT obs_date, rain_mm, data_origin
                FROM weather_daily
                WHERE plot_id = ? AND obs_date BETWEEN ? AND ?
                ORDER BY obs_date
                """, (rs, n) -> {
            LocalDate date = rs.getObject(1, LocalDate.class);
            return new WeatherResponse(date, dap(date, planting), rs.getBigDecimal(2),
                    DataOrigin.valueOf(rs.getString(3)));
        }, plotId, orEarliest(from), orLatest(to));
    }

    /** The newest baseline saved for the plot. No baseline yet is a normal state, so it returns no bins. */
    @Transactional(readOnly = true)
    public BaselineResponse baseline(AuthPrincipal principal, long plotId) {
        plots.get(principal, plotId);
        List<BaselineSet> latest = jdbc.query("""
                SELECT season_type, version, data_origin FROM baselines
                WHERE plot_id = ? ORDER BY id DESC LIMIT 1
                """, (rs, n) -> new BaselineSet(rs.getString(1), rs.getString(2),
                DataOrigin.valueOf(rs.getString(3))), plotId);
        if (latest.isEmpty()) {
            return new BaselineResponse(null, null, null, null, List.of());
        }

        BaselineSet set = latest.get(0);
        List<BaselineBinResponse> bins = jdbc.query("""
                SELECT dap_bin, p10, p50, p90, is_filled FROM baselines
                WHERE plot_id = ? AND season_type = ? AND version = ?
                ORDER BY dap_bin
                """, (rs, n) -> new BaselineBinResponse(rs.getInt(1), rs.getBigDecimal(2), rs.getBigDecimal(3),
                rs.getBigDecimal(4), rs.getBoolean(5)), plotId, set.seasonType(), set.version());
        return new BaselineResponse(set.seasonType(), set.version(), binDays(plotId), set.origin(), bins);
    }

    /** The newest status. A plot with no analysis yet reports NOT_ENOUGH_DATA, never a guess. */
    @Transactional(readOnly = true)
    public StatusResponse status(AuthPrincipal principal, long plotId) {
        plots.get(principal, plotId);
        Optional<LastKnown> lastKnown = lastKnown(plotId);
        return jdbc.query("""
                SELECT status_date, status, anomaly, rain_pct, cause, confidence, explanation_json,
                       model_version, data_origin, thresholds_json
                FROM plot_status
                WHERE plot_id = ?
                ORDER BY status_date DESC, id DESC
                LIMIT 1
                """, (rs, n) -> {
            String status = rs.getString(2);
            JsonNode explanation = readJson(rs.getString(7));
            Optional<LastKnown> shown = NOT_ENOUGH_DATA.equals(status) ? lastKnown : Optional.empty();
            return new StatusResponse(rs.getObject(1, LocalDate.class), status, rs.getBigDecimal(3),
                    rs.getBigDecimal(4), rs.getString(5), rs.getString(6),
                    explanation.path("needsFieldVisit").asBoolean(false), explanation.path("signals"),
                    explanation.path("dataQualityNote").asText(""), rs.getString(8),
                    DataOrigin.valueOf(rs.getString(9)), shown.map(LastKnown::status).orElse(null),
                    shown.map(LastKnown::date).orElse(null), readJson(rs.getString(10)));
        }, plotId).stream().findFirst().orElseGet(PlotDataService::notAnalysedYet);
    }

    /** The newest result that was a real status, so the app can show when it last knew something. */
    private Optional<LastKnown> lastKnown(long plotId) {
        return jdbc.query("""
                SELECT status, status_date FROM plot_status
                WHERE plot_id = ? AND status <> ?
                ORDER BY status_date DESC, id DESC
                LIMIT 1
                """, (rs, n) -> new LastKnown(rs.getString(1), rs.getObject(2, LocalDate.class)),
                plotId, NOT_ENOUGH_DATA).stream().findFirst();
    }

    private static StatusResponse notAnalysedYet() {
        return new StatusResponse(null, NOT_ENOUGH_DATA, null, null, null, "LOW", false,
                JsonNodeFactory.instance.arrayNode(), "This plot has not been analysed yet.", null, null, null, null,
                null);
    }

    /** The newest forecast, which is either a range or the reason there isn't one. */
    @Transactional(readOnly = true)
    public ForecastResponse forecast(AuthPrincipal principal, long plotId) {
        plots.get(principal, plotId);
        return jdbc.query("""
                SELECT forecast_date, low_pct, high_pct, confidence, reason, data_quality_note, model_version,
                       data_origin, thresholds_json
                FROM harvest_forecasts
                WHERE plot_id = ?
                ORDER BY forecast_date DESC, id DESC
                LIMIT 1
                """, (rs, n) -> new ForecastResponse(rs.getString(5) == null, rs.getObject(1, LocalDate.class),
                rs.getBigDecimal(2), rs.getBigDecimal(3), rs.getString(4), rs.getString(5), rs.getString(6),
                rs.getString(7), DataOrigin.valueOf(rs.getString(8)), readJson(rs.getString(9))), plotId)
                .stream().findFirst()
                .orElseGet(() -> new ForecastResponse(false, null, null, null, "LOW", "NOT_ANALYSED",
                        "This plot has not been analysed yet.", null, null, null));
    }

    @Transactional(readOnly = true)
    public EnrollmentResponse enrollment(AuthPrincipal principal, long plotId) {
        boolean demoPlot = plots.get(principal, plotId).isDemo();
        return jdbc.query("""
                SELECT e.status, s.code, e.start_date, e.end_date, e.sum_insured, e.premium, e.is_demo
                FROM enrollments e
                JOIN seasons s ON s.id = e.season_id
                WHERE e.plot_id = ?
                ORDER BY e.id DESC
                LIMIT 1
                """, (rs, n) -> new EnrollmentResponse(rs.getString(1), rs.getString(2),
                rs.getObject(3, LocalDate.class), rs.getObject(4, LocalDate.class), rs.getBigDecimal(5),
                rs.getBigDecimal(6), rs.getBoolean(7)), plotId)
                .stream().findFirst()
                .orElseGet(() -> new EnrollmentResponse("NOT_ENROLLED", null, null, null, null, null, demoPlot));
    }

    private JsonNode readJson(String json) {
        try {
            return json == null ? JsonNodeFactory.instance.nullNode() : mapper.readTree(json);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Checks the user can see the plot. Seeded plots have no date of their own, so the latest run's date is used. */
    private LocalDate plantingDate(AuthPrincipal principal, long plotId) {
        LocalDate own = plots.get(principal, plotId).plantingDate();
        if (own != null) {
            return own;
        }
        return jdbc.query("SELECT planting_date FROM ml_runs WHERE plot_id = ? ORDER BY id DESC LIMIT 1",
                (rs, n) -> rs.getObject(1, LocalDate.class), plotId).stream().findFirst().orElse(null);
    }

    private Integer binDays(long plotId) {
        return jdbc.query("""
                SELECT params_json->>'$.dapBinDays' FROM ml_runs
                WHERE plot_id = ? ORDER BY id DESC LIMIT 1
                """, (rs, n) -> rs.getObject(1, Integer.class), plotId).stream().findFirst().orElse(null);
    }

    private static Integer dap(LocalDate date, LocalDate planting) {
        return planting == null ? null : (int) ChronoUnit.DAYS.between(planting, date);
    }

    private static void checkRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DATE_RANGE_INVALID", "from is after to.");
        }
    }

    private static LocalDate orEarliest(LocalDate from) {
        return from == null ? EARLIEST : from;
    }

    private static LocalDate orLatest(LocalDate to) {
        return to == null ? LATEST : to;
    }
}
