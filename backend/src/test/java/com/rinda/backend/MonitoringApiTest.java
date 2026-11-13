package com.rinda.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.rinda.backend.FakeSatellite.FakeImage;
import com.rinda.backend.monitoring.SatelliteClient.MonitorRequest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Satellite monitoring (docs/02-satellite-monitoring.md), with the fake satellite client and fake data only. */
class MonitoringApiTest extends ApiTestBase {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Africa/Kigali"));
    private static final LocalDate PLANTED = TODAY.minusDays(40);

    private String farmer;
    private String agent;
    private String admin;

    @BeforeEach
    void accounts() throws Exception {
        farmer = farmerToken("+250789998881");
        agent = staffToken("+250789998882", "AGENT", cooperativeId);
        admin = staffToken("+250789998883", "ADMIN", null);
    }

    private JsonNode monitoring(String token, long plotId) throws Exception {
        String body = mvc.perform(get("/api/v1/plots/" + plotId + "/monitoring").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body);
    }

    private long walked(String name) throws Exception {
        long id = draftPlot(farmer, name, PLANTED.toString());
        walk(farmer, id, squareFor(id)).andExpect(status().isOk());
        return id;
    }

    private int refresh() throws Exception {
        String body = mvc.perform(post("/api/v1/admin/monitoring/refresh").header("Authorization", "Bearer " + admin))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("jobsQueued").asInt();
    }

    @Test
    void aPlotWithoutAWalkedBoundaryIsNotMonitored() throws Exception {
        long id = draftPlot(farmer, "Not walked", PLANTED.toString());

        JsonNode view = monitoring(farmer, id);
        assertThat(view.get("state").asText()).isEqualTo("NO_BOUNDARY");
        assertThat(view.get("message").asText()).contains("Walk the edge");
        assertThat(view.get("observations")).isEmpty();
        assertThat(view.get("latestImageDate").isNull()).isTrue();
        assertThat(satellite.calls).isEmpty();
        assertThat(refresh()).isZero();
        mvc.perform(get("/api/v1/plots/" + id).header("Authorization", "Bearer " + farmer))
                .andExpect(jsonPath("$.monitoring.state").value("NO_BOUNDARY"));
    }

    @Test
    void savingTheBoundaryStartsABackfillFromThePlantingDate() throws Exception {
        satellite.images = List.of(new FakeImage(PLANTED.plusDays(5), 0.21, 60),
                new FakeImage(PLANTED.plusDays(20), 0.33, 4), new FakeImage(PLANTED.plusDays(30), 0.41, 55));
        long id = walked("Hillside");

        assertThat(satellite.calls).hasSize(1);
        MonitorRequest call = satellite.calls.get(0);
        assertThat(call.source()).isEqualTo("GEE");
        assertThat(call.boundary()).isNotNull();
        assertThat(call.plantingDate()).isEqualTo(PLANTED);
        assertThat(call.fetchFrom()).isEqualTo(PLANTED);
        assertThat(call.asOf()).isEqualTo(TODAY);
        assertThat(call.minClearPixels()).isEqualTo(20);
        assertThat(call.history()).isEmpty();

        JsonNode view = monitoring(farmer, id);
        assertThat(view.get("state").asText()).isEqualTo("DATA_AVAILABLE");
        assertThat(view.get("latestImageDate").asText()).isEqualTo(PLANTED.plusDays(30).toString());
        assertThat(view.get("monitoredSince").asText()).isEqualTo(PLANTED.toString());
        assertThat(view.get("observations")).hasSize(3);
        // Raw values are kept; the 4-pixel image is shown as not usable rather than dropped.
        assertThat(view.at("/observations/1/pixelCount").asInt()).isEqualTo(4);
        assertThat(view.at("/observations/1/usable").asBoolean()).isFalse();
        assertThat(view.at("/observations/0/ndviMedian").decimalValue()).isEqualByComparingTo("0.21");
        assertThat(view.get("rainfall")).hasSize(3);
        assertThat(view.at("/latestJob/kind").asText()).isEqualTo("BACKFILL");
        assertThat(view.at("/latestJob/status").asText()).isEqualTo("DONE");
        assertThat(view.at("/seasonCheck/flag").asText()).isEqualTo("NOT_ENOUGH_DATA");
        assertThat(view.at("/seasonCheck/modelVersion").asText()).isEqualTo("0.6.0-test");
        assertThat(view.get("updateFrequency").asText()).isEqualTo("WEEKLY");
        assertThat(view.get("dataOrigin").asText()).isEqualTo("GEE");
        assertThat(view.get("message").asText()).contains("Not live");
        assertThat(view.get("notes").toString()).contains("never proof of crop");
        // Not verified yet: the curve is shown, but health status and claims wait for the field check.
        assertThat(view.get("verificationNote").asText()).contains("not verified");
        assertThat(view.get("healthStatusAvailable").asBoolean()).isFalse();

        mvc.perform(get("/api/v1/plots/" + id).header("Authorization", "Bearer " + farmer))
                .andExpect(jsonPath("$.monitoring.state").value("DATA_AVAILABLE"))
                .andExpect(jsonPath("$.monitoring.latestImageDate").value(PLANTED.plusDays(30).toString()))
                .andExpect(jsonPath("$.monitoring.seasonFlag").value("NOT_ENOUGH_DATA"));
        Integer modelAudit = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE action = 'MODEL_VERSION_ADDED' AND after_json->>'$.name' = 'season-check'",
                Integer.class);
        assertThat(modelAudit).isEqualTo(1);
    }

    @Test
    void noClearImageShowsRainfallOnly() throws Exception {
        satellite.images = List.of(new FakeImage(PLANTED.plusDays(5), 0.3, 3), new FakeImage(PLANTED.plusDays(15), null, 0));
        long id = walked("Cloudy");

        JsonNode view = monitoring(farmer, id);
        assertThat(view.get("state").asText()).isEqualTo("NO_CLEAR_IMAGE");
        assertThat(view.get("message").asText()).isEqualTo("No clear image since " + PLANTED + "; showing rainfall only.");
        assertThat(view.get("latestImageDate").isNull()).isTrue();
        assertThat(view.get("rainfall")).hasSize(2);
    }

    @Test
    void anEmptyMinimumPixelCountCountsNoImage() throws Exception {
        jdbc.update("UPDATE thresholds SET threshold_value = NULL WHERE threshold_key = 'min_clear_pixels'");
        try {
            satellite.images = List.of(new FakeImage(PLANTED.plusDays(5), 0.3, 500));
            long id = walked("No limit yet");

            assertThat(satellite.calls.get(0).minClearPixels()).isNull();
            JsonNode view = monitoring(farmer, id);
            assertThat(view.get("state").asText()).isEqualTo("NO_CLEAR_IMAGE");
            assertThat(view.get("minClearPixels").isNull()).isTrue();
            assertThat(view.at("/observations/0/usable").asBoolean()).isFalse();
        } finally {
            jdbc.update("UPDATE thresholds SET threshold_value = 20 WHERE threshold_key = 'min_clear_pixels'");
        }
    }

    @Test
    void whileTheFirstFetchIsQueuedTheScreenSaysFetching() throws Exception {
        long id = draftPlot(farmer, "Queued", PLANTED.toString());
        long walkId = insertWalk(id);
        jdbc.update("INSERT INTO satellite_jobs (plot_id, walk_id, kind, source, trigger_type, fetch_from, fetch_to) "
                + "VALUES (?, ?, 'BACKFILL', 'GEE', 'BOUNDARY', ?, ?)", id, walkId, PLANTED, TODAY);

        JsonNode view = monitoring(farmer, id);
        assertThat(view.get("state").asText()).isEqualTo("FETCHING");
        assertThat(view.get("message").asText()).isEqualTo("Fetching satellite data.");
        assertThat(view.get("refreshing").asBoolean()).isTrue();
        assertThat(refresh()).as("one job at a time per plot").isZero();
    }

    @Test
    void aFailedFetchIsShownAndTheNextRefreshTriesAgain() throws Exception {
        satellite.failures.add(FakeSatellite.earthEngineDown());
        satellite.failures.add(FakeSatellite.earthEngineDown());
        long id = walked("Unlucky");

        JsonNode view = monitoring(farmer, id);
        assertThat(view.get("state").asText()).isEqualTo("FETCH_FAILED");
        assertThat(view.at("/latestJob/errorCode").asText()).isEqualTo("ML_SERVICE_ERROR");
        assertThat(satellite.calls).hasSize(2);

        assertThat(refresh()).isEqualTo(1);
        assertThat(satellite.calls.get(2).fetchFrom()).as("still a backfill").isEqualTo(PLANTED);
        assertThat(monitoring(farmer, id).get("state").asText()).isEqualTo("NO_CLEAR_IMAGE");
    }

    @Test
    void theWeeklyRefreshFetchesOnlyNewImagesAndSendsTheStoredSeason() throws Exception {
        satellite.images = List.of(new FakeImage(PLANTED.plusDays(5), 0.2, 60), new FakeImage(PLANTED.plusDays(25), 0.3, 60));
        long id = walked("Weekly");
        draftPlot(farmer, "Never walked", PLANTED.toString());

        assertThat(refresh()).isEqualTo(1);
        MonitorRequest refresh = satellite.calls.get(1);
        assertThat(refresh.fetchFrom()).as("10 days of overlap for late images").isEqualTo(TODAY.minusDays(10));
        assertThat(refresh.asOf()).isEqualTo(TODAY);
        assertThat(refresh.history()).extracting(row -> row.date())
                .containsExactly(PLANTED.plusDays(5), PLANTED.plusDays(25));
        assertThat(monitoring(farmer, id).at("/latestJob/kind").asText()).isEqualTo("REFRESH");
    }

    @Test
    void anEarlierPlantingDateStartsANewBackfill() throws Exception {
        long id = walked("Moved date");
        LocalDate earlier = PLANTED.minusDays(14);

        send(farmer, put("/api/v1/plots/" + id), Map.of("name", "Moved date", "plantingDate", earlier.toString()))
                .andExpect(status().isOk());
        assertThat(satellite.calls).hasSize(2);
        assertThat(satellite.calls.get(1).fetchFrom()).isEqualTo(earlier);
        assertThat(monitoring(farmer, id).get("monitoredSince").asText()).isEqualTo(earlier.toString());
    }

    @Test
    void aNewWalkStartsAFreshSeries() throws Exception {
        satellite.images = List.of(new FakeImage(PLANTED.plusDays(5), 0.2, 60));
        long id = walked("Rewalked");
        walk(farmer, id, squareFor(id)).andExpect(status().isOk());

        assertThat(satellite.calls).hasSize(2);
        assertThat(satellite.calls.get(1).fetchFrom()).isEqualTo(PLANTED);
        assertThat(satellite.calls.get(1).history()).as("the old boundary's images are not reused").isEmpty();
        assertThat(monitoring(farmer, id).get("observations")).hasSize(1);
    }

    @Test
    void anInconsistentSeasonOpensAReviewFlagAndNeverRejects() throws Exception {
        satellite.flag = "INCONSISTENT";
        satellite.reason = "NO_GROWTH_SEEN";
        long id = pendingPlot(farmer, "Bare", PLANTED.toString());

        JsonNode plot = mapper.readTree(mvc.perform(get("/api/v1/plots/" + id).header("Authorization", "Bearer " + farmer))
                .andReturn().getResponse().getContentAsString());
        assertThat(plot.get("status").asText()).isEqualTo("FLAGGED");
        assertThat(plot.get("flags").toString()).contains("SATELLITE_INCONSISTENT").contains("not proof");
        assertThat(plot.at("/monitoring/seasonFlag").asText()).isEqualTo("INCONSISTENT");

        // A person reviews it; then the plot can go on to the field check.
        long flagId = jdbc.queryForObject("SELECT id FROM plot_flags WHERE plot_id = ? AND type = 'SATELLITE_INCONSISTENT'",
                Long.class, id);
        send(agent, post("/api/v1/plots/" + id + "/flags/" + flagId + "/resolve"),
                Map.of("note", "Maize seen at the plot; planted late after the rains."))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"));
    }

    @Test
    void onAFieldVerifiedPlotTheFlagIsShownButTheCheckStands() throws Exception {
        long id = pendingPlot(farmer, "Verified", PLANTED.toString());
        fieldVerified(id);
        satellite.flag = "INCONSISTENT";
        satellite.reason = "STILL_GREEN_AFTER_SEASON";
        refresh();

        mvc.perform(get("/api/v1/plots/" + id).header("Authorization", "Bearer " + farmer))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.flags[*].type", hasItem("SATELLITE_INCONSISTENT")));
        JsonNode view = monitoring(farmer, id);
        assertThat(view.get("verificationNote").isNull()).isTrue();
        assertThat(view.get("healthStatusAvailable").asBoolean()).isTrue();
    }

    @Test
    void demoPlotsGetOnlyTheSimulatedSample() throws Exception {
        long id = draftPlot(farmer, "Demo", PLANTED.toString());
        jdbc.update("UPDATE plots SET is_demo = TRUE WHERE id = ?", id);
        walk(farmer, id, squareFor(id)).andExpect(status().isOk());

        assertThat(satellite.calls.get(0).source()).isEqualTo("SAMPLE");
        assertThat(satellite.calls.get(0).boundary()).isNull();
        JsonNode view = monitoring(farmer, id);
        assertThat(view.get("dataOrigin").asText()).isEqualTo("SAMPLE_FILE");
        assertThat(view.get("isDemo").asBoolean()).isTrue();
    }

    @Test
    void monitoringIsVisibleOnlyToThoseWhoCanSeeThePlot() throws Exception {
        long id = walked("Private");
        mvc.perform(get("/api/v1/plots/" + id + "/monitoring")
                        .header("Authorization", "Bearer " + farmerToken("+250789998884")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PLOT_NOT_FOUND"));
        assertThat(monitoring(agent, id).get("plotId").asLong()).isEqualTo(id);
    }

    @Test
    void onlyAnAdminCanRunTheRefreshByHand() throws Exception {
        mvc.perform(post("/api/v1/admin/monitoring/refresh").header("Authorization", "Bearer " + farmer))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/monitoring/refresh").header("Authorization", "Bearer " + agent))
                .andExpect(status().isForbidden());
    }

    private long insertWalk(long plotId) {
        long farmerId = jdbc.queryForObject("SELECT farmer_id FROM plots WHERE id = ?", Long.class, plotId);
        jdbc.update("""
                INSERT INTO gps_walks (plot_id, point_count, worst_accuracy_m, mock_detected, walked_area_m2, started_at,
                                       ended_at, submitted_by)
                VALUES (?, 4, 4, FALSE, 1000, NOW(6), NOW(6), ?)
                """, plotId, farmerId);
        return jdbc.queryForObject("SELECT MAX(id) FROM gps_walks WHERE plot_id = ?", Long.class, plotId);
    }
}
