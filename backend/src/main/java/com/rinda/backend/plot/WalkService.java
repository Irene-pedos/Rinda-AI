package com.rinda.backend.plot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rinda.backend.auth.AuthPrincipal;
import com.rinda.backend.common.ApiException;
import com.rinda.backend.common.ThresholdService;
import com.rinda.backend.monitoring.MonitoringRequested;
import com.rinda.backend.plot.PlotDtos.GeoJsonPolygon;
import com.rinda.backend.plot.PlotDtos.PlotResponse;
import com.rinda.backend.plot.PlotDtos.WalkPoint;
import com.rinda.backend.plot.PlotDtos.WalkRequest;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only way a plot gets a boundary: the farmer walks its edge and the app sends the GPS fixes. The server
 * builds the polygon itself from those points; no endpoint accepts a polygon.
 *
 * <p>Honest limit: the server can check the points (order, accuracy, mock flag, shape) but cannot prove they
 * were walked. A tampered app could send invented points. That is why payouts need a person's field check.
 *
 * <p>Walk points are personal data: never log them.
 */
@Service
public class WalkService {

    private final PlotService plotService;
    private final PlotRepository plots;
    private final PlotGeometry geometry;
    private final ThresholdService thresholds;
    private final PlotFlags flags;
    private final PlotChecks checks;
    private final PlotRules rules;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final ApplicationEventPublisher events;

    public WalkService(PlotService plotService, PlotRepository plots, PlotGeometry geometry,
                       ThresholdService thresholds, PlotFlags flags, PlotChecks checks, PlotRules rules,
                       JdbcTemplate jdbc, ObjectMapper mapper, ApplicationEventPublisher events) {
        this.events = events;
        this.plotService = plotService;
        this.plots = plots;
        this.geometry = geometry;
        this.thresholds = thresholds;
        this.flags = flags;
        this.checks = checks;
        this.rules = rules;
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional
    public PlotResponse submit(AuthPrincipal farmer, long plotId, WalkRequest request) {
        Plot plot = plotService.findOwned(farmer, plotId);
        if (plot.getVerificationLevel().atLeast(VerificationLevel.FIELD_VERIFIED)) {
            throw new ApiException(HttpStatus.CONFLICT, "PLOT_LOCKED",
                    "The boundary can't change after the field check. Ask your agent.");
        }
        if (flags.openTypes(plotId).stream().anyMatch(FlagType::isDispute)) {
            throw new ApiException(HttpStatus.CONFLICT, "PLOT_DISPUTED",
                    "This plot is disputed. Your agent must resolve it before the plot can be walked again.");
        }
        List<WalkPoint> points = request.points();
        checkOrder(points);
        double worstAccuracy = points.stream().mapToDouble(WalkPoint::accuracyM).max().orElseThrow();
        Optional<BigDecimal> accuracyLimit = thresholds.value("walk_max_accuracy_m");
        accuracyLimit.ifPresent(limit -> rejectInaccurate(points, limit));

        GeoJsonPolygon polygon = toPolygon(points);
        String geojson = toJson(polygon);
        BigDecimal areaHa = measure(polygon, geojson);
        BigDecimal areaM2 = areaHa.multiply(BigDecimal.valueOf(10_000)).setScale(1, java.math.RoundingMode.HALF_UP);
        boolean mock = points.stream().anyMatch(WalkPoint::mockLocation);

        jdbc.update("UPDATE gps_walks SET status = 'SUPERSEDED' WHERE plot_id = ? AND status = 'CURRENT'", plotId);
        long walkId = insertWalk(plotId, farmer.userId(), points, worstAccuracy, mock, areaM2);
        insertPoints(walkId, points);

        plot.setWalkedBoundary(geojson, areaHa, areaM2);
        flags.resolveLegacyBoundary(plotId, farmer.userId());
        if (accuracyLimit.isEmpty()) {
            flags.raise(plotId, FlagType.GPS_ACCURACY_UNCHECKED, "The GPS accuracy limit is not set yet, so a "
                    + "person must review this walk. The least accurate point was "
                    + BigDecimal.valueOf(worstAccuracy).stripTrailingZeros().toPlainString() + " m.", null);
        }
        if (mock) {
            flags.raise(plotId, FlagType.MOCK_LOCATION,
                    "The phone reported a mock-location app during the walk.", null);
        }
        plots.saveAndFlush(plot);
        checks.area(plot);
        checks.overlap(plot);
        rules.refresh(plot);
        // Monitoring starts only now that the plot has a saved boundary; the job is queued after commit.
        events.publishEvent(new com.rinda.backend.monitoring.MonitoringRequested(plotId));
        return plotService.toResponse(plots.saveAndFlush(plot));
    }

    private static void checkOrder(List<WalkPoint> points) {
        for (int i = 1; i < points.size(); i++) {
            if (!points.get(i).recordedAt().isAfter(points.get(i - 1).recordedAt())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "WALK_INVALID",
                        "Walk points must be in the order they were recorded, each later than the one before.");
            }
        }
        Set<List<Double>> distinct = new HashSet<>();
        points.forEach(p -> distinct.add(List.of(p.lng(), p.lat())));
        if (distinct.size() < 3) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "WALK_INVALID",
                    "The walk needs at least 3 different corners.");
        }
    }

    private static void rejectInaccurate(List<WalkPoint> points, BigDecimal limit) {
        long poor = points.stream().filter(p -> BigDecimal.valueOf(p.accuracyM()).compareTo(limit) > 0).count();
        if (poor > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "WALK_ACCURACY_POOR",
                    poor + " of " + points.size() + " points were less accurate than "
                            + limit.stripTrailingZeros().toPlainString()
                            + " m. Wait for a better GPS signal and walk the plot again.");
        }
    }

    /** [longitude, latitude] in walk order, closed back to the first point. */
    private static GeoJsonPolygon toPolygon(List<WalkPoint> points) {
        List<List<Double>> ring = new ArrayList<>();
        // Arrays.asList, not List.of: PlotGeometry checks contains(null), which List.of rejects.
        points.forEach(p -> ring.add(java.util.Arrays.asList(p.lng(), p.lat())));
        if (!ring.get(0).equals(ring.get(ring.size() - 1))) {
            ring.add(ring.get(0));
        }
        return new GeoJsonPolygon("Polygon", List.of(ring));
    }

    private BigDecimal measure(GeoJsonPolygon boundary, String geojson) {
        BigDecimal area = geometry.validateAndMeasure(boundary, geojson);
        thresholds.value("plot_min_area_ha").filter(min -> area.compareTo(min) < 0).ifPresent(min -> {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PLOT_TOO_SMALL",
                    "The plot must be at least " + min.stripTrailingZeros().toPlainString()
                            + " hectares for reliable satellite readings.");
        });
        thresholds.value("plot_max_area_ha").filter(max -> area.compareTo(max) > 0).ifPresent(max -> {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PLOT_TOO_LARGE",
                    "The plot must be at most " + max.stripTrailingZeros().toPlainString() + " hectares.");
        });
        return area;
    }

    private long insertWalk(long plotId, long userId, List<WalkPoint> points, double worstAccuracy, boolean mock,
                            BigDecimal areaM2) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO gps_walks (plot_id, point_count, worst_accuracy_m, mock_detected, walked_area_m2,
                                           started_at, ended_at, submitted_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, plotId);
            ps.setInt(2, points.size());
            ps.setDouble(3, worstAccuracy);
            ps.setBoolean(4, mock);
            ps.setBigDecimal(5, areaM2);
            ps.setTimestamp(6, Timestamp.from(points.get(0).recordedAt()));
            ps.setTimestamp(7, Timestamp.from(points.get(points.size() - 1).recordedAt()));
            ps.setLong(8, userId);
            return ps;
        }, key);
        return key.getKey().longValue();
    }

    private void insertPoints(long walkId, List<WalkPoint> points) {
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            WalkPoint p = points.get(i);
            rows.add(new Object[] {walkId, i, p.lat(), p.lng(), p.accuracyM(), Timestamp.from(p.recordedAt()),
                    p.mockLocation()});
        }
        jdbc.batchUpdate("""
                INSERT INTO gps_walk_points (walk_id, seq, lat, lng, accuracy_m, recorded_at, mock_location)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, rows);
    }

    private String toJson(GeoJsonPolygon polygon) {
        try {
            return mapper.writeValueAsString(polygon);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
