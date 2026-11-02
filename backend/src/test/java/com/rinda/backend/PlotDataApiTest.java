package com.rinda.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class PlotDataApiTest extends ApiTestBase {

    private static final String KEY = "test-only-key-test-only-key-test-only-key";
    private static final String PLANTED = "2026-09-20";
    private static final List<List<Double>> SQUARE = List.of(List.of(30.100, -1.950), List.of(30.102, -1.950),
            List.of(30.102, -1.948), List.of(30.100, -1.948), List.of(30.100, -1.950));

    private String farmer;
    private long plotId;

    @BeforeEach
    void createPlot() throws Exception {
        farmer = farmerToken("+250785555551");
        Map<String, Object> plot = Map.of("name", "Field", "plantingDate", PLANTED,
                "boundary", Map.of("type", "Polygon", "coordinates", List.of(SQUARE)));
        String body = mvc.perform(post("/api/v1/plots").header("Authorization", "Bearer " + farmer)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(plot)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        plotId = mapper.readTree(body).get("id").asLong();
        jdbc.update("UPDATE plots SET status = 'ACTIVE' WHERE id = ?", plotId);
    }

    private void saveResult(String plantingDate) throws Exception {
        Map<String, Object> result = new HashMap<>();
        result.put("plotId", plotId);
        result.put("dataOrigin", "GEE");
        result.put("modelVersion", "0.1.0");
        result.put("plantingDate", plantingDate);
        result.put("inputFrom", plantingDate);
        result.put("inputTo", "2026-10-20");
        result.put("params", Map.of("dapBinDays", 10));
        result.put("observations", List.of(
                Map.of("date", "2026-10-10", "source", "S2", "clearPixels", 3),
                Map.of("date", "2026-10-05", "source", "S2", "ndvi", 0.41, "smoothedNdvi", 0.40,
                        "clearPixels", 240, "cloudPct", 12.5)));
        result.put("weather", List.of(Map.of("date", "2026-10-02", "rainMm", 0),
                Map.of("date", "2026-10-01", "rainMm", 4.2)));
        result.put("baseline", Map.of("seasonType", "A", "version", "plot-history-v1", "bins", List.of(
                Map.of("dapBin", 20, "p10", 0.3, "p50", 0.4, "p90", 0.5, "filled", true),
                Map.of("dapBin", 10, "p10", 0.2, "p50", 0.3, "p90", 0.4))));
        mvc.perform(post("/api/v1/internal/ml/results").header("X-Internal-Api-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(result)))
                .andExpect(status().isOk());
    }

    private ResultActions read(String token, String path) throws Exception {
        return mvc.perform(get("/api/v1/plots/" + plotId + path).header("Authorization", "Bearer " + token));
    }

    @Test
    void observationsAreOldestFirstWithDaysAfterPlanting() throws Exception {
        saveResult(PLANTED);

        read(farmer, "/observations")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].date").value("2026-10-05"))
                .andExpect(jsonPath("$[0].dap").value(15))
                .andExpect(jsonPath("$[0].ndvi").value(0.41))
                .andExpect(jsonPath("$[0].smoothedNdvi").value(0.40))
                .andExpect(jsonPath("$[0].dataOrigin").value("GEE"));
    }

    @Test
    void cloudyDateStaysAGapInsteadOfAReading() throws Exception {
        saveResult(PLANTED);

        read(farmer, "/observations")
                .andExpect(jsonPath("$[1].date").value("2026-10-10"))
                .andExpect(jsonPath("$[1].ndvi").value(nullValue()))
                .andExpect(jsonPath("$[1].clearPixels").value(3));
    }

    @Test
    void dateRangeFiltersObservations() throws Exception {
        saveResult(PLANTED);

        read(farmer, "/observations?from=2026-10-06&to=2026-10-31")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].date").value("2026-10-10"));
    }

    @Test
    void backwardsDateRangeIsRejected() throws Exception {
        read(farmer, "/weather?from=2026-10-06&to=2026-10-01")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DATE_RANGE_INVALID"));
    }

    @Test
    void badDateHasACode() throws Exception {
        read(farmer, "/observations?from=06-10-2026")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_INVALID"));
    }

    @Test
    void weatherIsOldestFirstAndZeroRainIsKept() throws Exception {
        saveResult(PLANTED);

        read(farmer, "/weather")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].date").value("2026-10-01"))
                .andExpect(jsonPath("$[0].rainMm").value(4.2))
                .andExpect(jsonPath("$[1].rainMm").value(0));
    }

    @Test
    void baselineIsSortedAndNamesItsSource() throws Exception {
        saveResult(PLANTED);

        read(farmer, "/baseline")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seasonType").value("A"))
                .andExpect(jsonPath("$.version").value("plot-history-v1"))
                .andExpect(jsonPath("$.binDays").value(10))
                .andExpect(jsonPath("$.dataOrigin").value("GEE"))
                .andExpect(jsonPath("$.bins[0].dapBin").value(10))
                .andExpect(jsonPath("$.bins[1].dapBin").value(20))
                .andExpect(jsonPath("$.bins[0].filled").value(false))
                .andExpect(jsonPath("$.bins[1].p50").value(0.4))
                .andExpect(jsonPath("$.bins[1].filled").value(true));
    }

    @Test
    void plotWithNoAnalysisYetIsEmptyNotAnError() throws Exception {
        read(farmer, "/observations").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        read(farmer, "/weather").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        read(farmer, "/baseline")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bins", hasSize(0)))
                .andExpect(jsonPath("$.version").value(nullValue()));
    }

    @Test
    void placeholderPlotUsesThePlantingDateOfItsLatestRun() throws Exception {
        saveResult("2026-09-30");
        jdbc.update("UPDATE plots SET planting_date = NULL WHERE id = ?", plotId);

        read(farmer, "/observations").andExpect(jsonPath("$[0].dap").value(5));
    }

    @Test
    void placeholderPlotWithNoRunHasNoDaysAfterPlanting() throws Exception {
        saveResult(PLANTED);
        jdbc.update("UPDATE plots SET planting_date = NULL WHERE id = ?", plotId);
        jdbc.update("DELETE FROM ml_runs WHERE plot_id = ?", plotId);

        read(farmer, "/weather").andExpect(jsonPath("$[0].dap").value(nullValue()));
    }

    private Map<String, Object> statusBody(String date, String status, String cause) {
        Map<String, Object> body = new HashMap<>();
        body.put("statusDate", date);
        body.put("status", status);
        body.put("anomaly", -1.25);
        body.put("rainPct", 42.5);
        body.put("cause", cause);
        body.put("confidence", "MEDIUM");
        body.put("explanation", Map.of("needsFieldVisit", false, "dataQualityNote", "2 clear reading(s).",
                "signals", List.of(Map.of("code", "NDVI_BELOW_NORMAL", "readings", 2))));
        body.put("thresholdsUsed", Map.of("at_risk_anomaly", -1.5, "not_enough_data_window_days", 20));
        return body;
    }

    private ResultActions saveStatus(Map<String, Object> status) throws Exception {
        Map<String, Object> result = new HashMap<>();
        result.put("plotId", plotId);
        result.put("dataOrigin", "GEE");
        result.put("modelVersion", "0.2.0");
        result.put("plantingDate", PLANTED);
        result.put("inputFrom", PLANTED);
        result.put("inputTo", "2026-10-20");
        result.put("params", Map.of());
        result.put("observations", List.of());
        result.put("weather", List.of());
        result.put("status", status);
        return mvc.perform(post("/api/v1/internal/ml/results").header("X-Internal-Api-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(result)));
    }

    @Test
    void plotWithNoAnalysisReportsNotEnoughDataNeverAGuess() throws Exception {
        read(farmer, "/status")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_ENOUGH_DATA"))
                .andExpect(jsonPath("$.confidence").value("LOW"))
                .andExpect(jsonPath("$.date").value(nullValue()))
                .andExpect(jsonPath("$.signals", hasSize(0)));
    }

    @Test
    void statusCarriesConfidenceQualityNoteAndVersion() throws Exception {
        saveStatus(statusBody("2026-10-20", "AT_RISK", "WATER_DEFICIT")).andExpect(status().isOk());

        read(farmer, "/status")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-10-20"))
                .andExpect(jsonPath("$.status").value("AT_RISK"))
                .andExpect(jsonPath("$.anomaly").value(-1.25))
                .andExpect(jsonPath("$.rainPct").value(42.5))
                .andExpect(jsonPath("$.cause").value("WATER_DEFICIT"))
                .andExpect(jsonPath("$.confidence").value("MEDIUM"))
                .andExpect(jsonPath("$.needsFieldVisit").value(false))
                .andExpect(jsonPath("$.signals[0].code").value("NDVI_BELOW_NORMAL"))
                .andExpect(jsonPath("$.dataQualityNote").value("2 clear reading(s)."))
                .andExpect(jsonPath("$.modelVersion").value("0.2.0"))
                .andExpect(jsonPath("$.dataOrigin").value("GEE"));
    }

    @Test
    void statusStoresAndReturnsTheThresholdsItUsed() throws Exception {
        saveStatus(statusBody("2026-10-20", "AT_RISK", "UNEXPLAINED")).andExpect(status().isOk());

        read(farmer, "/status")
                .andExpect(jsonPath("$.thresholdsUsed.at_risk_anomaly").value(-1.5))
                .andExpect(jsonPath("$.thresholdsUsed.not_enough_data_window_days").value(20));
        assertThat(jdbc.queryForObject("SELECT thresholds_json->>'$.at_risk_anomaly' FROM plot_status", String.class))
                .isEqualTo("-1.5");
    }

    @Test
    void statusWithoutItsThresholdsIsRefused() throws Exception {
        Map<String, Object> status = statusBody("2026-10-20", "WATCH", null);
        status.remove("thresholdsUsed");

        saveStatus(status).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void notEnoughDataStillGivesTheLastKnownStatusAndDate() throws Exception {
        saveStatus(statusBody("2026-10-01", "HEALTHY", null)).andExpect(status().isOk());
        saveStatus(statusBody("2026-10-10", "WATCH", null)).andExpect(status().isOk());
        saveStatus(statusBody("2026-10-20", "NOT_ENOUGH_DATA", null)).andExpect(status().isOk());
        saveStatus(statusBody("2026-10-25", "NOT_ENOUGH_DATA", null)).andExpect(status().isOk());

        read(farmer, "/status")
                .andExpect(jsonPath("$.status").value("NOT_ENOUGH_DATA"))
                .andExpect(jsonPath("$.date").value("2026-10-25"))
                .andExpect(jsonPath("$.lastKnownStatus").value("WATCH"))
                .andExpect(jsonPath("$.lastKnownDate").value("2026-10-10"));
    }

    @Test
    void lastKnownStatusIsOnlySentWithNotEnoughData() throws Exception {
        saveStatus(statusBody("2026-10-10", "WATCH", null)).andExpect(status().isOk());
        saveStatus(statusBody("2026-10-20", "HEALTHY", null)).andExpect(status().isOk());

        read(farmer, "/status").andExpect(jsonPath("$.lastKnownStatus").value(nullValue()));
    }

    @Test
    void notEnoughDataWithNoEarlierStatusHasNoLastKnownStatus() throws Exception {
        saveStatus(statusBody("2026-10-20", "NOT_ENOUGH_DATA", null)).andExpect(status().isOk());

        read(farmer, "/status")
                .andExpect(jsonPath("$.lastKnownStatus").value(nullValue()))
                .andExpect(jsonPath("$.lastKnownDate").value(nullValue()));
    }

    @Test
    void teamDecisionsAreInTheTableAndMarkedAsDemoNotFinal() {
        Map<String, String> expected = Map.of("min_clear_pixels", "20.000", "not_enough_data_window_days", "20.000",
                "not_enough_data_min_obs", "1.000", "watch_anomaly", "-1.000", "watch_rain_pct", "60.000",
                "at_risk_anomaly", "-1.500", "at_risk_consecutive_obs", "2.000", "at_risk_min_days", "10.000",
                "trigger_min_valid_obs", "2.000");

        expected.forEach((key, value) -> {
            assertThat(jdbc.queryForObject("SELECT threshold_value FROM thresholds WHERE threshold_key = ?",
                    String.class, key)).as(key).isEqualTo(value);
            assertThat(jdbc.queryForObject("SELECT status FROM thresholds WHERE threshold_key = ?",
                    String.class, key)).as(key).isEqualTo("CONFIRMED_FOR_DEMO");
        });
    }

    @Test
    void thresholdChangesFromTheDecisionsAreAudited() {
        assertThat(jdbc.queryForObject("""
                SELECT before_json->>'$.value' FROM audit_log
                WHERE action = 'THRESHOLD_CHANGED' AND after_json->>'$.key' = 'at_risk_anomaly'
                """, String.class)).startsWith("-1.000");
        assertThat(jdbc.queryForObject("""
                SELECT after_json->>'$.value' FROM audit_log
                WHERE action = 'THRESHOLD_CHANGED' AND after_json->>'$.key' = 'at_risk_anomaly'
                """, String.class)).startsWith("-1.500");
    }

    @Test
    void newestStatusWins() throws Exception {
        saveStatus(statusBody("2026-10-20", "WATCH", null)).andExpect(status().isOk());
        saveStatus(statusBody("2026-10-10", "HEALTHY", null)).andExpect(status().isOk());

        read(farmer, "/status").andExpect(jsonPath("$.status").value("WATCH"));
    }

    @Test
    void rerunForTheSameDateReplacesTheStatus() throws Exception {
        saveStatus(statusBody("2026-10-20", "WATCH", null)).andExpect(status().isOk());
        saveStatus(statusBody("2026-10-20", "HEALTHY", null)).andExpect(status().isOk());

        read(farmer, "/status").andExpect(jsonPath("$.status").value("HEALTHY"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM plot_status", Long.class)).isEqualTo(1);
    }

    @Test
    void statusRowPointsAtTheRunThatMadeIt() throws Exception {
        saveStatus(statusBody("2026-10-20", "WATCH", null)).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("""
                SELECT r.model_version FROM plot_status s JOIN ml_runs r ON r.id = s.run_id
                """, String.class)).isEqualTo("0.2.0");
    }

    @Test
    void aCauseNamingAPestIsRefused() throws Exception {
        saveStatus(statusBody("2026-10-20", "AT_RISK", "FALL_ARMYWORM"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void thresholdsNeedTheInternalKey() throws Exception {
        mvc.perform(get("/api/v1/internal/ml/thresholds"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INTERNAL_KEY_INVALID"));

        mvc.perform(get("/api/v1/internal/ml/thresholds").header("X-Internal-Api-Key", KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.watch_rain_pct").value(60.0))
                .andExpect(jsonPath("$.trigger_rain_deficit_pct").value(nullValue()));
    }

    @Test
    void anotherFarmerGetsTheSame404AsAMissingPlot() throws Exception {
        String other = farmerToken("+250785555552");

        for (String path : List.of("/observations", "/weather", "/baseline", "/status")) {
            read(other, path).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PLOT_NOT_FOUND"));
        }
    }

    @Test
    void agentSeesDataForTheirCooperativeOnly() throws Exception {
        String own = staffToken("+250785555553", "AGENT", cooperativeId);
        String outside = staffToken("+250785555554", "AGENT", newCooperative("Elsewhere"));

        read(own, "/baseline").andExpect(status().isOk());
        read(outside, "/baseline").andExpect(status().isNotFound());
    }

    @Test
    void loginIsRequired() throws Exception {
        mvc.perform(get("/api/v1/plots/" + plotId + "/observations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
