package com.rinda.backend.plot;

import com.rinda.backend.common.ApiException;
import com.rinda.backend.plot.PlotDtos.GeoJsonPolygon;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Validates plot polygons and measures them with MySQL's geographic functions. */
@Component
public class PlotGeometry {

    // Rwanda's bounding box. It also covers border areas of neighbouring countries;
    // the agent's verification step catches those (docs/DECISIONS.md).
    private static final double MIN_LON = 28.85;
    private static final double MAX_LON = 30.90;
    private static final double MIN_LAT = -2.85;
    private static final double MAX_LAT = -1.04;
    private static final int MAX_POINTS = 500;

    private final JdbcTemplate jdbc;

    public PlotGeometry(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Throws if the polygon is unusable; otherwise returns its area in hectares. */
    public BigDecimal validateAndMeasure(GeoJsonPolygon polygon, String geojson) {
        checkShape(polygon);
        try {
            Integer valid = jdbc.queryForObject("SELECT ST_IsValid(ST_GeomFromGeoJSON(?))", Integer.class, geojson);
            if (valid == null || valid != 1) {
                throw invalid("The boundary must not cross itself.");
            }
            return jdbc.queryForObject("SELECT ROUND(ST_Area(ST_GeomFromGeoJSON(?)) / 10000, 4)",
                    BigDecimal.class, geojson);
        } catch (DataAccessException ex) {
            throw invalid("The boundary is not a valid polygon.");
        }
    }

    private static void checkShape(GeoJsonPolygon polygon) {
        if (!"Polygon".equals(polygon.type())) {
            throw invalid("The boundary must be a GeoJSON Polygon.");
        }
        if (polygon.coordinates().size() != 1) {
            throw invalid("The boundary must have exactly one ring, with no holes.");
        }
        List<List<Double>> ring = polygon.coordinates().get(0);
        if (ring == null || ring.size() < 4 || ring.size() > MAX_POINTS) {
            throw invalid("The boundary must have between 4 and " + MAX_POINTS + " points.");
        }
        for (List<Double> point : ring) {
            if (point == null || point.size() != 2 || point.contains(null)) {
                throw invalid("Each point must be [longitude, latitude].");
            }
            double lon = point.get(0);
            double lat = point.get(1);
            if (lon < MIN_LON || lon > MAX_LON || lat < MIN_LAT || lat > MAX_LAT) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "PLOT_OUTSIDE_RWANDA",
                        "The boundary must be inside Rwanda.");
            }
        }
        if (!ring.get(0).equals(ring.get(ring.size() - 1))) {
            throw invalid("The boundary must end at its first point.");
        }
    }

    private static ApiException invalid(String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, "PLOT_GEOMETRY_INVALID", detail);
    }
}
