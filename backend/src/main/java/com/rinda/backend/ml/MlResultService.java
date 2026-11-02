package com.rinda.backend.ml;

import com.rinda.backend.common.ApiException;
import com.rinda.backend.ml.MlDtos.BaselineBin;
import com.rinda.backend.ml.MlDtos.BaselineResult;
import com.rinda.backend.ml.MlDtos.DataOrigin;
import com.rinda.backend.ml.MlDtos.MlResultRequest;
import com.rinda.backend.ml.MlDtos.MlResultResponse;
import com.rinda.backend.ml.MlDtos.ObservationRow;
import com.rinda.backend.ml.MlDtos.WeatherRow;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;
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

    public MlResultService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private record PlotInfo(String status, String dataStatus, boolean demo) {
    }

    @Transactional
    public MlResultResponse save(MlResultRequest result) {
        checkOriginAllowed(result);
        checkContent(result);

        jdbc.update("INSERT IGNORE INTO model_versions (name, version) VALUES (?, ?)",
                MODEL_NAME, result.modelVersion());
        long runId = insertRun(result);
        saveObservations(result);
        saveWeather(result);
        int bins = saveBaseline(result);
        return new MlResultResponse(runId, result.observations().size(), result.weather().size(), bins);
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

    private static void checkContent(MlResultRequest result) {
        if (result.inputFrom().isAfter(result.inputTo())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INPUT_DATES_INVALID", "inputFrom is after inputTo.");
        }
        if (result.baseline() != null) {
            for (BaselineBin bin : result.baseline().bins()) {
                if (bin.p10().compareTo(bin.p50()) > 0 || bin.p50().compareTo(bin.p90()) > 0) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "BASELINE_INVALID",
                            "Baseline percentiles must rise from p10 to p90.");
                }
            }
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
                INSERT INTO baselines (plot_id, season_type, dap_bin, p10, p50, p90, version, data_origin)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, baseline.bins(), 200, (ps, bin) -> {
            ps.setLong(1, result.plotId());
            ps.setString(2, baseline.seasonType());
            ps.setInt(3, bin.dapBin());
            ps.setBigDecimal(4, bin.p10());
            ps.setBigDecimal(5, bin.p50());
            ps.setBigDecimal(6, bin.p90());
            ps.setString(7, baseline.version());
            ps.setString(8, result.dataOrigin().name());
        });
        return baseline.bins().size();
    }
}
