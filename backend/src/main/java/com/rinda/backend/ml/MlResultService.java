package com.rinda.backend.ml;

import com.rinda.backend.claim.ClaimTriggerService;
import com.rinda.backend.common.ApiException;
import com.rinda.backend.ml.MlDtos.BaselineBin;
import com.rinda.backend.ml.MlDtos.BaselineResult;
import com.rinda.backend.ml.MlDtos.DataOrigin;
import com.rinda.backend.ml.MlDtos.ForecastResult;
import com.rinda.backend.ml.MlDtos.MlResultRequest;
import com.rinda.backend.ml.MlDtos.MlResultResponse;
import com.rinda.backend.ml.MlDtos.ObservationRow;
import com.rinda.backend.ml.MlDtos.StatusResult;
import com.rinda.backend.ml.MlDtos.WeatherRow;
import com.rinda.backend.notification.NotificationService;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stores ML results. Saving the same result twice leaves the data unchanged, apart from a new ml_runs row. */
@Service
public class MlResultService {

    private static final String MODEL_NAME = "rinda-extract";

    private final JdbcTemplate jdbc;
    private final NotificationService notifications;
    private final ClaimTriggerService claimTrigger;

    public MlResultService(JdbcTemplate jdbc, NotificationService notifications, ClaimTriggerService claimTrigger) {
        this.jdbc = jdbc;
        this.notifications = notifications;
        this.claimTrigger = claimTrigger;
    }

    private record PlotInfo(String status, String dataStatus, boolean demo) {
    }

    @Transactional
    public MlResultResponse save(MlResultRequest result) {
        checkOriginAllowed(result);
        checkContent(result);

        recordModelVersion(result.modelVersion());
        long runId = insertRun(result);
        saveObservations(result);
        saveWeather(result);
        int bins = saveBaseline(result);
        saveStatus(result, runId);
        saveForecast(result, runId);
        // The parametric trigger runs on every saved result, in the same transaction (spec §3.7).
        claimTrigger.evaluate(result.plotId());
        return new MlResultResponse(runId, result.observations().size(), result.weather().size(), bins);
    }

    /** The first result from a new model version is audited, so every change of model can be traced. */
    private void recordModelVersion(String version) {
        int added = jdbc.update("INSERT IGNORE INTO model_versions (name, version) VALUES (?, ?)", MODEL_NAME, version);
        if (added == 1) {
            jdbc.update("""
                    INSERT INTO audit_log (action, entity, after_json)
                    VALUES ('MODEL_VERSION_ADDED', 'model_versions', JSON_OBJECT('name', ?, 'version', ?))
                    """, MODEL_NAME, version);
        }
    }

    /** A re-run for the same date and model replaces the row, which now points at the newer run. */
    private void saveStatus(MlResultRequest result, long runId) {
        StatusResult status = result.status();
        if (status == null) {
            return;
        }
        Optional<NotificationService.LastStatus> before = notifications.lastKnownStatus(result.plotId());
        jdbc.update("""
                INSERT INTO plot_status (plot_id, status_date, status, anomaly, rain_pct, cause, confidence,
                                         explanation_json, thresholds_json, model_version, data_origin, run_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) AS new
                ON DUPLICATE KEY UPDATE status = new.status, anomaly = new.anomaly, rain_pct = new.rain_pct,
                    cause = new.cause, confidence = new.confidence, explanation_json = new.explanation_json,
                    thresholds_json = new.thresholds_json, data_origin = new.data_origin, run_id = new.run_id
                """, result.plotId(), Date.valueOf(status.statusDate()), status.status(), status.anomaly(),
                status.rainPct(), status.cause(), status.confidence(), status.explanation().toString(),
                status.thresholdsUsed().toString(), result.modelVersion(), result.dataOrigin().name(), runId);
        notifications.healthChanged(result.plotId(), status.status(), status.cause(), status.statusDate(), before,
                result.dataOrigin() == DataOrigin.SAMPLE_FILE);
    }

    /** Sample data can only reach demo plots, and real data only verified plots with confirmed details. */
    private void checkOriginAllowed(MlResultRequest result) {
        PlotInfo plot = jdbc.query("SELECT status, data_status, is_demo FROM plots WHERE id = ?",
                        (rs, n) -> new PlotInfo(rs.getString(1), rs.getString(2), rs.getBoolean(3)), result.plotId())
                .stream().findFirst()
                .filter(p -> !"ARCHIVED".equals(p.status()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PLOT_NOT_FOUND", "Plot not found."));

        if (result.dataOrigin() == DataOrigin.SAMPLE_FILE && !plot.demo()) {
            throw new ApiException(HttpStatus.CONFLICT, "SAMPLE_ON_REAL_PLOT",
                    "Sample data can only be stored on demo plots.");
        }
        if (result.dataOrigin() == DataOrigin.GEE && !"CONFIRMED".equals(plot.dataStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "PLOT_NOT_CONFIRMED",
                    "This plot's location and planting date are not confirmed.");
        }
        if (result.dataOrigin() == DataOrigin.GEE && !"ACTIVE".equals(plot.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "PLOT_NOT_ACTIVE", "Only verified plots are analysed.");
        }
    }

    private void saveForecast(MlResultRequest result, long runId) {
        ForecastResult forecast = result.forecast();
        if (forecast == null) {
            return;
        }
        jdbc.update("""
                INSERT INTO harvest_forecasts (plot_id, forecast_date, low_pct, high_pct, model_version, confidence,
                                               data_origin, run_id, reason, data_quality_note, thresholds_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) AS new
                ON DUPLICATE KEY UPDATE low_pct = new.low_pct, high_pct = new.high_pct, confidence = new.confidence,
                    data_origin = new.data_origin, run_id = new.run_id, reason = new.reason,
                    data_quality_note = new.data_quality_note, thresholds_json = new.thresholds_json
                """, result.plotId(), Date.valueOf(forecast.forecastDate()), forecast.lowPct(), forecast.highPct(),
                result.modelVersion(), forecast.confidence(), result.dataOrigin().name(), runId, forecast.reason(),
                forecast.dataQualityNote(), forecast.thresholdsUsed().toString());
    }

    private static void checkContent(MlResultRequest result) {
        if (result.inputFrom().isAfter(result.inputTo())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INPUT_DATES_INVALID", "inputFrom is after inputTo.");
        }
        checkForecast(result.forecast());
        if (result.baseline() != null) {
            for (BaselineBin bin : result.baseline().bins()) {
                if (bin.p10().compareTo(bin.p50()) > 0 || bin.p50().compareTo(bin.p90()) > 0) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "BASELINE_INVALID",
                            "Baseline percentiles must rise from p10 to p90.");
                }
            }
        }
    }

    /** A forecast is a real range or a reason, never one number and never both. */
    private static void checkForecast(ForecastResult forecast) {
        if (forecast == null) {
            return;
        }
        boolean hasRange = forecast.lowPct() != null && forecast.highPct() != null;
        boolean noRange = forecast.lowPct() == null && forecast.highPct() == null;
        boolean valid = forecast.reason() == null
                ? hasRange && forecast.lowPct().compareTo(forecast.highPct()) < 0
                : noRange;
        if (!valid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FORECAST_INVALID",
                    "A forecast needs a low and a higher high, or a reason and no range.");
        }
    }

    private long insertRun(MlResultRequest result) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO ml_runs (plot_id, data_origin, model_version, planting_date, input_from, input_to, params_json)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, result.plotId());
            ps.setString(2, result.dataOrigin().name());
            ps.setString(3, result.modelVersion());
            ps.setDate(4, Date.valueOf(result.plantingDate()));
            ps.setDate(5, Date.valueOf(result.inputFrom()));
            ps.setDate(6, Date.valueOf(result.inputTo()));
            ps.setString(7, result.params().toString());
            return ps;
        }, keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }

    private void saveObservations(MlResultRequest result) {
        jdbc.batchUpdate("""
                INSERT INTO observations (plot_id, obs_date, source, ndvi, smoothed_ndvi, clear_pixels, cloud_pct, data_origin)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?) AS new
                ON DUPLICATE KEY UPDATE ndvi = new.ndvi, smoothed_ndvi = new.smoothed_ndvi,
                    clear_pixels = new.clear_pixels, cloud_pct = new.cloud_pct, data_origin = new.data_origin
                """, result.observations(), 200, (ps, row) -> bindObservation(ps, result, row));
    }

    private static void bindObservation(PreparedStatement ps, MlResultRequest result, ObservationRow row)
            throws java.sql.SQLException {
        ps.setLong(1, result.plotId());
        ps.setDate(2, Date.valueOf(row.date()));
        ps.setString(3, row.source());
        ps.setBigDecimal(4, row.ndvi());
        ps.setBigDecimal(5, row.smoothedNdvi());
        ps.setObject(6, row.clearPixels());
        ps.setBigDecimal(7, row.cloudPct());
        ps.setString(8, result.dataOrigin().name());
    }

    private void saveWeather(MlResultRequest result) {
        List<WeatherRow> rows = result.weather();
        jdbc.batchUpdate("""
                INSERT INTO weather_daily (plot_id, obs_date, rain_mm, data_origin)
                VALUES (?, ?, ?, ?) AS new
                ON DUPLICATE KEY UPDATE rain_mm = new.rain_mm, data_origin = new.data_origin
                """, rows, 200, (ps, row) -> {
            ps.setLong(1, result.plotId());
            ps.setDate(2, Date.valueOf(row.date()));
            ps.setBigDecimal(3, row.rainMm());
            ps.setString(4, result.dataOrigin().name());
        });
    }

    private int saveBaseline(MlResultRequest result) {
        BaselineResult baseline = result.baseline();
        if (baseline == null) {
            return 0;
        }
        // Replaced rather than upserted: a NULL district stops the unique key from matching.
        jdbc.update("DELETE FROM baselines WHERE plot_id = ? AND season_type = ? AND version = ?",
                result.plotId(), baseline.seasonType(), baseline.version());
        jdbc.batchUpdate("""
                INSERT INTO baselines (plot_id, season_type, dap_bin, p10, p50, p90, version, data_origin, is_filled)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, baseline.bins(), 200, (ps, bin) -> {
            ps.setLong(1, result.plotId());
            ps.setString(2, baseline.seasonType());
            ps.setInt(3, bin.dapBin());
            ps.setBigDecimal(4, bin.p10());
            ps.setBigDecimal(5, bin.p50());
            ps.setBigDecimal(6, bin.p90());
            ps.setString(7, baseline.version());
            ps.setString(8, result.dataOrigin().name());
            ps.setBoolean(9, bin.filled());
        });
        return baseline.bins().size();
    }
}
