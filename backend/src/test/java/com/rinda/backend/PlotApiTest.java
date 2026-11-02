package com.rinda.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class PlotApiTest extends ApiTestBase {

    private static final String FARMER = "+250782222221";
    private static final String OTHER_FARMER = "+250782222222";

    /** About 222 m on each side, so roughly 4.9 hectares. */
    private static final List<List<Double>> SQUARE = List.of(
            point(30.100, -1.950), point(30.102, -1.950), point(30.102, -1.948),
            point(30.100, -1.948), point(30.100, -1.950));

    private static List<Double> point(double lon, double lat) {
        return List.of(lon, lat);
    }

    private static Map<String, Object> polygon(List<List<Double>> ring) {
        return Map.of("type", "Polygon", "coordinates", List.of(ring));
    }

    private static Map<String, Object> plotBody(List<List<Double>> ring) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "Hillside");
        body.put("boundary", polygon(ring));
        body.put("plantingDate", LocalDate.now().minusDays(10).toString());
        return body;
    }

    private ResultActions postPlot(String token, Object body) throws Exception {
        return mvc.perform(post("/api/v1/plots").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)));
    }

    private ResultActions putPlot(String token, long id, Object body) throws Exception {
        return mvc.perform(put("/api/v1/plots/" + id).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)));
    }

    private ResultActions getUrl(String token, String url) throws Exception {
        return mvc.perform(get(url).header("Authorization", "Bearer " + token));
    }

    private long createPlot(String token) throws Exception {
        String body = postPlot(token, plotBody(SQUARE)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("id").asLong();
    }

    private void activate(long plotId) {
        jdbc.update("UPDATE plots SET status = 'ACTIVE' WHERE id = ?", plotId);
    }

    private long newSeason() {
        jdbc.update("INSERT INTO seasons (code, type) VALUES ('T1', 'A')");
        return jdbc.queryForObject("SELECT id FROM seasons WHERE code = 'T1'", Long.class);
    }

    @Test
    void newPlotIsMaizeAndPendingVerification() throws Exception {
        postPlot(farmerToken(FARMER), plotBody(SQUARE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.crop").value("MAIZE"))
                .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"))
                .andExpect(jsonPath("$.isDemo").value(false))
                .andExpect(jsonPath("$.areaHa").value(closeTo(4.9, 0.1)));
    }

    @Test
    void savedBoundaryKeepsLongitudeAndLatitudeInTheRightPlaces() throws Exception {
        long id = createPlot(farmerToken(FARMER));
        Double lat = jdbc.queryForObject("SELECT ST_Latitude(ST_PointN(ST_ExteriorRing(boundary), 1)) FROM plots WHERE id = ?",
                Double.class, id);
        Double lon = jdbc.queryForObject("SELECT ST_Longitude(ST_PointN(ST_ExteriorRing(boundary), 1)) FROM plots WHERE id = ?",
                Double.class, id);
        assertThat(lat).isEqualTo(-1.95);
        assertThat(lon).isEqualTo(30.1);
    }

    @Test
    void ringDirectionDoesNotChangeTheArea() throws Exception {
        String token = farmerToken(FARMER);
        List<List<Double>> reversed = new ArrayList<>(SQUARE);
        java.util.Collections.reverse(reversed);
        postPlot(token, plotBody(reversed))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.areaHa").value(closeTo(4.9, 0.1)));
    }

    @Test
    void otherCropsAreRejected() throws Exception {
        Map<String, Object> body = plotBody(SQUARE);
        body.put("crop", "BEANS");
        postPlot(farmerToken(FARMER), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CROP_NOT_SUPPORTED"));
    }

    @Test
    void plotOutsideRwandaIsRejected() throws Exception {
        List<List<Double>> nairobi = List.of(point(36.80, -1.30), point(36.81, -1.30), point(36.81, -1.29),
                point(36.80, -1.30));
        postPlot(farmerToken(FARMER), plotBody(nairobi))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLOT_OUTSIDE_RWANDA"));
    }

    @Test
    void selfCrossingBoundaryIsRejected() throws Exception {
        List<List<Double>> bowTie = List.of(point(30.100, -1.950), point(30.102, -1.948), point(30.102, -1.950),
                point(30.100, -1.948), point(30.100, -1.950));
        postPlot(farmerToken(FARMER), plotBody(bowTie))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLOT_GEOMETRY_INVALID"));
    }

    @Test
    void unclosedBoundaryIsRejected() throws Exception {
        postPlot(farmerToken(FARMER), plotBody(SQUARE.subList(0, 4)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLOT_GEOMETRY_INVALID"));
    }

    @Test
    void plotBelowMinimumAreaIsRejected() throws Exception {
        List<List<Double>> tiny = List.of(point(30.1000, -1.9500), point(30.1001, -1.9500), point(30.1001, -1.9499),
                point(30.1000, -1.9499), point(30.1000, -1.9500));
        postPlot(farmerToken(FARMER), plotBody(tiny))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLOT_TOO_SMALL"));
    }

    @Test
    void futurePlantingDateIsRejected() throws Exception {
        Map<String, Object> body = plotBody(SQUARE);
        body.put("plantingDate", LocalDate.now().plusDays(3).toString());
        postPlot(farmerToken(FARMER), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLANTING_DATE_INVALID"));
    }

    @Test
    void confirmedSeasonWindowIsEnforced() throws Exception {
        jdbc.update("INSERT INTO seasons (code, type, start_date, end_date, data_status) VALUES ('T1', 'A', '2001-01-01', '2001-02-01', 'CONFIRMED')");
        postPlot(farmerToken(FARMER), plotBody(SQUARE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLANTING_DATE_INVALID"));
    }

    @Test
    void missingFieldsAreReportedByName() throws Exception {
        postPlot(farmerToken(FARMER), Map.of("name", "No boundary"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.boundary").exists())
                .andExpect(jsonPath("$.fields.plantingDate").exists());
    }

    @Test
    void farmerCannotSeeOrChangeAnotherFarmersPlot() throws Exception {
        long id = createPlot(farmerToken(FARMER));
        String other = farmerToken(OTHER_FARMER);

        getUrl(other, "/api/v1/plots/" + id).andExpect(status().isNotFound());
        putPlot(other, id, plotBody(SQUARE)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/plots/" + id).header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
        getUrl(other, "/api/v1/plots").andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void agentSeesOnlyTheirOwnCooperative() throws Exception {
        long id = createPlot(farmerToken(FARMER));
        String sameCoop = staffToken("+250783333331", "AGENT", cooperativeId);
        String otherCoop = staffToken("+250783333332", "AGENT", newCooperative("Other Cooperative"));

        getUrl(sameCoop, "/api/v1/plots/" + id).andExpect(status().isOk());
        getUrl(sameCoop, "/api/v1/plots").andExpect(jsonPath("$.totalItems").value(1));
        getUrl(otherCoop, "/api/v1/plots/" + id).andExpect(status().isNotFound());
        getUrl(otherCoop, "/api/v1/plots").andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void officerAndAdminSeeAllPlotsButCannotCreateThem() throws Exception {
        long id = createPlot(farmerToken(FARMER));
        String officer = staffToken("+250783333333", "OFFICER", null);
        String admin = staffToken("+250783333334", "ADMIN", null);

        getUrl(officer, "/api/v1/plots/" + id).andExpect(status().isOk());
        getUrl(admin, "/api/v1/plots").andExpect(jsonPath("$.totalItems").value(1));
        postPlot(officer, plotBody(SQUARE))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void listIsPaginatedAndBoundsAreChecked() throws Exception {
        String token = farmerToken(FARMER);
        createPlot(token);
        createPlot(token);
        createPlot(token);

        getUrl(token, "/api/v1/plots?page=1&size=2")
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
        getUrl(token, "/api/v1/plots?size=1000")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAGE_INVALID"));
    }

    @Test
    void pendingPlotCanBeEdited() throws Exception {
        String token = farmerToken(FARMER);
        long id = createPlot(token);
        List<List<Double>> bigger = List.of(point(30.100, -1.950), point(30.104, -1.950), point(30.104, -1.948),
                point(30.100, -1.948), point(30.100, -1.950));

        Map<String, Object> body = plotBody(bigger);
        body.put("name", "Renamed");
        putPlot(token, id, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.areaHa").value(closeTo(9.8, 0.2)));

        String area = jdbc.queryForObject("SELECT ROUND(ST_Area(boundary) / 10000, 1) FROM plots WHERE id = ?",
                String.class, id);
        assertThat(Double.parseDouble(area)).isBetween(9.6, 10.0);
    }

    @Test
    void verifiedPlotOnlyAllowsRenaming() throws Exception {
        String token = farmerToken(FARMER);
        long id = createPlot(token);
        activate(id);

        Map<String, Object> rename = plotBody(SQUARE);
        rename.put("name", "New name");
        putPlot(token, id, rename)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New name"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        Map<String, Object> newDate = plotBody(SQUARE);
        newDate.put("plantingDate", LocalDate.now().minusDays(20).toString());
        putPlot(token, id, newDate)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLOT_LOCKED"));
    }

    @Test
    void rejectedPlotReturnsToVerificationWhenCorrected() throws Exception {
        String token = farmerToken(FARMER);
        long id = createPlot(token);
        jdbc.update("UPDATE plots SET status = 'REJECTED' WHERE id = ?", id);

        Map<String, Object> corrected = plotBody(SQUARE);
        corrected.put("plantingDate", LocalDate.now().minusDays(20).toString());
        putPlot(token, id, corrected)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"));
    }

    @Test
    void deletedPlotDisappearsButTheRecordIsKept() throws Exception {
        String token = farmerToken(FARMER);
        long id = createPlot(token);

        mvc.perform(delete("/api/v1/plots/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        getUrl(token, "/api/v1/plots/" + id).andExpect(status().isNotFound());
        getUrl(token, "/api/v1/plots").andExpect(jsonPath("$.totalItems").value(0));
        assertThat(jdbc.queryForObject("SELECT status FROM plots WHERE id = ?", String.class, id)).isEqualTo("ARCHIVED");
    }

    @Test
    void plotWithActiveCoverCannotBeDeleted() throws Exception {
        String token = farmerToken(FARMER);
        long id = createPlot(token);
        jdbc.update("INSERT INTO enrollments (plot_id, season_id, status) VALUES (?, ?, 'COVERED')", id, newSeason());

        mvc.perform(delete("/api/v1/plots/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLOT_DELETE_DEFERRED"));
    }

    @Test
    void plotWithFinishedCoverCanBeDeleted() throws Exception {
        String token = farmerToken(FARMER);
        long id = createPlot(token);
        jdbc.update("INSERT INTO enrollments (plot_id, season_id, status, end_date) VALUES (?, ?, 'COVERED', '2001-01-01')",
                id, newSeason());

        mvc.perform(delete("/api/v1/plots/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void plotWithOpenClaimCannotBeDeleted() throws Exception {
        String token = farmerToken(FARMER);
        long id = createPlot(token);
        jdbc.update("""
                INSERT INTO claims (plot_id, season_id, trigger_date, evidence_snapshot_json, model_version)
                VALUES (?, ?, CURRENT_DATE, '{}', 'test')
                """, id, newSeason());

        mvc.perform(delete("/api/v1/plots/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLOT_DELETE_DEFERRED"));
    }
}
