package com.rinda.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/** Plot CRUD, visibility and geometry. The registration rules are in PlotRegistrationApiTest. */
class PlotApiTest extends ApiTestBase {

    private static final String FARMER = "+250782222221";
    private static final String OTHER_FARMER = "+250782222222";

    private static final String PLANTED = LocalDate.now().minusDays(10).toString();

    private Map<String, Object> body() {
        return registration("Hillside", PLANTED, "0/00/00/00/1");
    }

    private ResultActions postPlot(String token, Object body) throws Exception {
        return send(token, post("/api/v1/plots"), body);
    }

    private ResultActions putPlot(String token, long id, Object body) throws Exception {
        return send(token, put("/api/v1/plots/" + id), body);
    }

    private ResultActions getUrl(String token, String url) throws Exception {
        return mvc.perform(get(url).header("Authorization", "Bearer " + token));
    }

    private static Map<String, Object> edit(String name, String plantingDate) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("plantingDate", plantingDate);
        return body;
    }

    private long newSeason() {
        jdbc.update("INSERT INTO seasons (code, type) VALUES ('T1', 'A')");
        return jdbc.queryForObject("SELECT id FROM seasons WHERE code = 'T1'", Long.class);
    }

    @Test
    void newPlotIsMaizeAndADraftWithNoBoundaryUntilWalked() throws Exception {
        postPlot(farmerToken(FARMER), body())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.crop").value("MAIZE"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.verificationLevel").value("SELF_DECLARED"))
                .andExpect(jsonPath("$.financialActionsAllowed").value(false))
                .andExpect(jsonPath("$.boundary").doesNotExist())
                .andExpect(jsonPath("$.isDemo").value(false));
    }

    @Test
    void walkedBoundaryKeepsLongitudeAndLatitudeInTheRightPlaces() throws Exception {
        String token = farmerToken(FARMER);
        long id = draftPlot(token, "Hillside", PLANTED);
        walk(token, id, squareFor(id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.areaHa").value(closeTo(4.9, 0.1)))
                .andExpect(jsonPath("$.walkedAreaM2").value(closeTo(49_200, 1_000)));
        Double lat = jdbc.queryForObject("SELECT ST_Latitude(ST_PointN(ST_ExteriorRing(boundary), 1)) FROM plots WHERE id = ?",
                Double.class, id);
        Double lon = jdbc.queryForObject("SELECT ST_Longitude(ST_PointN(ST_ExteriorRing(boundary), 1)) FROM plots WHERE id = ?",
                Double.class, id);
        assertThat(lat).isCloseTo(squareFor(id).get(0)[1], org.assertj.core.data.Offset.offset(1e-9));
        assertThat(lon).isCloseTo(squareFor(id).get(0)[0], org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void walkingTheOtherWayRoundGivesTheSameArea() throws Exception {
        String token = farmerToken(FARMER);
        long id = draftPlot(token, "Hillside", PLANTED);
        List<double[]> reversed = new ArrayList<>(squareFor(id));
        Collections.reverse(reversed);
        walk(token, id, reversed).andExpect(status().isOk())
                .andExpect(jsonPath("$.areaHa").value(closeTo(4.9, 0.1)));
    }

    @Test
    void otherCropsAreRejected() throws Exception {
        Map<String, Object> body = body();
        body.put("crop", "BEANS");
        postPlot(farmerToken(FARMER), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CROP_NOT_SUPPORTED"));
    }

    @Test
    void walkOutsideRwandaIsRejected() throws Exception {
        String token = farmerToken(FARMER);
        long id = draftPlot(token, "Hillside", PLANTED);
        List<double[]> nairobi = List.of(new double[] {36.80, -1.30}, new double[] {36.81, -1.30},
                new double[] {36.81, -1.29});
        walk(token, id, nairobi)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLOT_OUTSIDE_RWANDA"));
    }

    @Test
    void selfCrossingWalkIsRejected() throws Exception {
        String token = farmerToken(FARMER);
        long id = draftPlot(token, "Hillside", PLANTED);
        List<double[]> bowTie = List.of(new double[] {30.100, -1.950}, new double[] {30.102, -1.948},
                new double[] {30.102, -1.950}, new double[] {30.100, -1.948});
        walk(token, id, bowTie)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLOT_GEOMETRY_INVALID"));
    }

    @Test
    void walkBelowMinimumAreaIsRejected() throws Exception {
        String token = farmerToken(FARMER);
        long id = draftPlot(token, "Hillside", PLANTED);
        List<double[]> tiny = List.of(new double[] {30.1000, -1.9500}, new double[] {30.1001, -1.9500},
                new double[] {30.1001, -1.9499}, new double[] {30.1000, -1.9499});
        walk(token, id, tiny)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLOT_TOO_SMALL"));
    }

    @Test
    void futurePlantingDateIsRejected() throws Exception {
        Map<String, Object> body = body();
        body.put("plantingDate", LocalDate.now().plusDays(3).toString());
        postPlot(farmerToken(FARMER), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLANTING_DATE_INVALID"));
    }

    @Test
    void confirmedSeasonWindowIsEnforced() throws Exception {
        jdbc.update("INSERT INTO seasons (code, type, start_date, end_date, data_status) VALUES ('T1', 'A', '2001-01-01', '2001-02-01', 'CONFIRMED')");
        postPlot(farmerToken(FARMER), body())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLANTING_DATE_INVALID"));
    }

    @Test
    void missingFieldsAreReportedByName() throws Exception {
        postPlot(farmerToken(FARMER), Map.of("name", "No details"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.upi").exists())
                .andExpect(jsonPath("$.fields.relationshipToPlot").exists())
                .andExpect(jsonPath("$.fields.declarationAccepted").exists())
                .andExpect(jsonPath("$.fields.plantingDate").exists());
    }

    @Test
    void farmerCannotSeeOrChangeAnotherFarmersPlot() throws Exception {
        long id = draftPlot(farmerToken(FARMER), "Hillside", PLANTED);
        String other = farmerToken(OTHER_FARMER);

        getUrl(other, "/api/v1/plots/" + id).andExpect(status().isNotFound());
        putPlot(other, id, edit("Mine now", PLANTED)).andExpect(status().isNotFound());
        walk(other, id, squareFor(id)).andExpect(status().isNotFound());
        uploadETitle(other, id, 1000).andExpect(status().isNotFound());
        getUrl(other, "/api/v1/plots/" + id + "/documents").andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/plots/" + id).header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
        getUrl(other, "/api/v1/plots").andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void agentSeesOnlyTheirOwnCooperative() throws Exception {
        long id = draftPlot(farmerToken(FARMER), "Hillside", PLANTED);
        String sameCoop = staffToken("+250783333331", "AGENT", cooperativeId);
        String otherCoop = staffToken("+250783333332", "AGENT", newCooperative("Other Cooperative"));

        getUrl(sameCoop, "/api/v1/plots/" + id).andExpect(status().isOk());
        getUrl(sameCoop, "/api/v1/plots").andExpect(jsonPath("$.totalItems").value(1));
        getUrl(otherCoop, "/api/v1/plots/" + id).andExpect(status().isNotFound());
        getUrl(otherCoop, "/api/v1/plots").andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void officerAndAdminSeeAllPlotsButCannotCreateOrWalkThem() throws Exception {
        long id = draftPlot(farmerToken(FARMER), "Hillside", PLANTED);
        String officer = staffToken("+250783333333", "OFFICER", null);
        String admin = staffToken("+250783333334", "ADMIN", null);

        getUrl(officer, "/api/v1/plots/" + id).andExpect(status().isOk());
        getUrl(admin, "/api/v1/plots").andExpect(jsonPath("$.totalItems").value(1));
        postPlot(officer, body())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        walk(admin, id, squareFor(id)).andExpect(status().isForbidden());
    }

    @Test
    void listIsPaginatedAndBoundsAreChecked() throws Exception {
        String token = farmerToken(FARMER);
        draftPlot(token, "A", PLANTED);
        draftPlot(token, "B", PLANTED);
        draftPlot(token, "C", PLANTED);

        getUrl(token, "/api/v1/plots?page=1&size=2")
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
        getUrl(token, "/api/v1/plots?size=1000")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAGE_INVALID"));
    }

    @Test
    void unverifiedPlotCanBeRenamedAndItsDateCorrected() throws Exception {
        String token = farmerToken(FARMER);
        long id = draftPlot(token, "Hillside", PLANTED);
        String newDate = LocalDate.now().minusDays(20).toString();
        putPlot(token, id, edit("Renamed", newDate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.plantingDate").value(newDate));
    }

    @Test
    void editResponseShowsTheSameUpdatedTimeAsALaterRead() throws Exception {
        String token = farmerToken(FARMER);
        String created = postPlot(token, body()).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = mapper.readTree(created).get("id").asLong();

        String edited = putPlot(token, id, edit("Renamed", PLANTED)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String read = getUrl(token, "/api/v1/plots/" + id).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Instant before = Instant.parse(mapper.readTree(created).get("updatedAt").asText());
        Instant after = Instant.parse(mapper.readTree(edited).get("updatedAt").asText());
        assertThat(after).isAfter(before);
        assertThat(after).isEqualTo(Instant.parse(mapper.readTree(read).get("updatedAt").asText()));
    }

    @Test
    void verifiedPlotOnlyAllowsRenaming() throws Exception {
        String token = farmerToken(FARMER);
        long id = pendingPlot(token, "Hillside", PLANTED);
        fieldVerified(id);

        putPlot(token, id, edit("New name", PLANTED))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New name"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        putPlot(token, id, edit("New name", LocalDate.now().minusDays(20).toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLOT_LOCKED"));
        walk(token, id, squareFor(id + 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLOT_LOCKED"));
    }

    @Test
    void rejectedPlotIsRegisteredAgainByWalkingIt() throws Exception {
        String token = farmerToken(FARMER);
        long id = pendingPlot(token, "Hillside", PLANTED);
        jdbc.update("UPDATE plots SET status = 'REJECTED', rejection_reason = 'Wrong field' WHERE id = ?", id);

        walk(token, id, squareFor(id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"));
        assertThat(jdbc.queryForObject("SELECT rejection_reason FROM plots WHERE id = ?", String.class, id)).isNull();
    }

    @Test
    void deletedPlotDisappearsButTheRecordIsKept() throws Exception {
        String token = farmerToken(FARMER);
        long id = draftPlot(token, "Hillside", PLANTED);

        mvc.perform(delete("/api/v1/plots/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        getUrl(token, "/api/v1/plots/" + id).andExpect(status().isNotFound());
        getUrl(token, "/api/v1/plots").andExpect(jsonPath("$.totalItems").value(0));
        assertThat(jdbc.queryForObject("SELECT status FROM plots WHERE id = ?", String.class, id)).isEqualTo("ARCHIVED");
    }

    @Test
    void plotWithActiveCoverCannotBeDeleted() throws Exception {
        String token = farmerToken(FARMER);
        long id = draftPlot(token, "Hillside", PLANTED);
        jdbc.update("INSERT INTO enrollments (plot_id, season_id, status) VALUES (?, ?, 'COVERED')", id, newSeason());

        mvc.perform(delete("/api/v1/plots/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLOT_DELETE_DEFERRED"));
    }

    @Test
    void plotWithFinishedCoverCanBeDeleted() throws Exception {
        String token = farmerToken(FARMER);
        long id = draftPlot(token, "Hillside", PLANTED);
        jdbc.update("INSERT INTO enrollments (plot_id, season_id, status, end_date) VALUES (?, ?, 'COVERED', '2001-01-01')",
                id, newSeason());

        mvc.perform(delete("/api/v1/plots/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void plotWithOpenClaimCannotBeDeleted() throws Exception {
        String token = farmerToken(FARMER);
        long id = draftPlot(token, "Hillside", PLANTED);
        jdbc.update("""
                INSERT INTO claims (plot_id, season_id, trigger_date, evidence_snapshot_json, model_version)
                VALUES (?, ?, CURRENT_DATE, '{}', 'test')
                """, id, newSeason());

        mvc.perform(delete("/api/v1/plots/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLOT_DELETE_DEFERRED"));
    }
}
