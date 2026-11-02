package com.rinda.backend.plot;

import com.rinda.backend.auth.AuthPrincipal;
import com.rinda.backend.common.ApiException;
import com.rinda.backend.ml.MlDtos.DataOrigin;
import com.rinda.backend.plot.PlotDataDtos.BaselineBinResponse;
import com.rinda.backend.plot.PlotDataDtos.BaselineResponse;
import com.rinda.backend.plot.PlotDataDtos.ObservationResponse;
import com.rinda.backend.plot.PlotDataDtos.WeatherResponse;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads satellite and rain results for the plot charts. Visibility follows the plot itself. */
@Service
public class PlotDataService {

    private static final LocalDate EARLIEST = LocalDate.of(1000, 1, 1);
    private static final LocalDate LATEST = LocalDate.of(9999, 12, 31);

    private final PlotService plots;
    private final JdbcTemplate jdbc;

    public PlotDataService(PlotService plots, JdbcTemplate jdbc) {
        this.plots = plots;
        this.jdbc = jdbc;
    }

    private record BaselineSet(String seasonType, String version, DataOrigin origin) {
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
                SELECT dap_bin, p10, p50, p90 FROM baselines
                WHERE plot_id = ? AND season_type = ? AND version = ?
                ORDER BY dap_bin
                """, (rs, n) -> new BaselineBinResponse(rs.getInt(1), rs.getBigDecimal(2), rs.getBigDecimal(3),
                rs.getBigDecimal(4)), plotId, set.seasonType(), set.version());
        return new BaselineResponse(set.seasonType(), set.version(), binDays(plotId), set.origin(), bins);
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
