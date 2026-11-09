package com.rinda.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * The parametric trigger (spec §3.7). Planting is 2026-09-20, so the sensitive stage (days 50-100)
 * runs from 2026-11-09 to 2026-12-29. Test rule: X = 10 days, Y = 80%, Z = 40%.
 */
class ClaimTriggerApiTest extends ApiTestBase {

    private static final String KEY = "test-only-key-test-only-key-test-only-key";
    private static final List<List<Double>> SQUARE = List.of(List.of(30.100, -1.950), List.of(30.102, -1.950),
            List.of(30.102, -1.948), List.of(30.100, -1.948), List.of(30.100, -1.950));

    private String farmerToken;
    private long plotId;
    private long seasonId;

    @BeforeEach
    void coveredActivePlot() throws Exception {
        farmerToken = farmerToken("+250785555551");
        Map<String, Object> plot = Map.of("name", "River field", "plantingDate", "2026-09-20",
                "boundary", Map.of("type", "Polygon", "coordinates", List.of(SQUARE)));
        String body = mvc.perform(post("/api/v1/plots").header("Authorization", "Bearer " + farmerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(plot)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        plotId = mapper.readTree(body).get("id").asLong();
        jdbc.update("UPDATE plots SET status = 'ACTIVE' WHERE id = ?", plotId);

        jdbc.update("INSERT INTO seasons (code, type) VALUES ('2026A', 'A')");
        seasonId = jdbc.queryForObject("SELECT id FROM seasons WHERE code = '2026A'", Long.class);
        jdbc.update("INSERT INTO enrollments (plot_id, season_id, status) VALUES (?, ?, 'COVERED')", plotId, seasonId);
        setRule(10, 80, 40);
    }

    /** thresholds is not cleaned between test classes, so put the trigger values back to "not supplied". */
    @AfterEach
    void clearRule() {
        setRule(null, null, null);
    }

    private void setRule(Integer days, Integer forecastPct, Integer rainDeficitPct) {
        jdbc.update("UPDATE thresholds SET threshold_value = ? WHERE threshold_key = 'trigger_at_risk_days'", days);
        jdbc.update("UPDATE thresholds SET threshold_value = ? WHERE threshold_key = 'trigger_forecast_pct'", forecastPct);
        jdbc.update("UPDATE thresholds SET threshold_value = ? WHERE threshold_key = 'trigger_rain_deficit_pct'",
                rainDeficitPct);
    }

    /** One ML run: a status on {@code date}, a clear S2 reading that day, and optionally a forecast range. */
    private void sendStatus(String date, String status, Double rainPct, Double forecastLow, Double forecastHigh)
            throws Exception {
        Map<String, Object> result = new HashMap<>();
        result.put("plotId", plotId);
        result.put("dataOrigin", "GEE");
        result.put("modelVersion", "0.3.0");
        result.put("plantingDate", "2026-09-20");
        result.put("inputFrom", "2026-09-20");
        result.put("inputTo", date);
        result.put("params", Map.of());
        result.put("observations", List.of(Map.of("date", date, "source", "S2", "ndvi", 0.31, "smoothedNdvi", 0.30,
                "clearPixels", 240, "cloudPct", 5)));
        result.put("weather", List.of(Map.of("date", date, "rainMm", 0.5)));
        Map<String, Object> statusBlock = new HashMap<>();
        statusBlock.put("statusDate", date);
        statusBlock.put("status", status);
        statusBlock.put("anomaly", "AT_RISK".equals(status) ? -1.8 : null);
        statusBlock.put("rainPct", rainPct);
        statusBlock.put("cause", "AT_RISK".equals(status) ? "WATER_DEFICIT" : null);
        statusBlock.put("confidence", "MEDIUM");
        statusBlock.put("explanation", Map.of("needsFieldVisit", false, "signals", List.of()));
        statusBlock.put("thresholdsUsed", Map.of());
        result.put("status", statusBlock);
        if (forecastLow != null) {
            result.put("forecast", Map.of("forecastDate", date, "lowPct", forecastLow, "highPct", forecastHigh,
                    "confidence", "LOW", "dataQualityNote", "Prototype estimate.", "thresholdsUsed", Map.of()));
        }
        mvc.perform(post("/api/v1/internal/ml/results").header("X-Internal-Api-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(result)))
                .andExpect(status().isOk());
    }

    private long claims() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM claims", Long.class);
    }

    @Test
    void claimStartsWhenTheWholeRuleIsMet() throws Exception {
        sendStatus("2026-11-15", "AT_RISK", 45.0, null, null);
        assertThat(claims()).isZero(); // one reading is not 10 days
        sendStatus("2026-11-29", "AT_RISK", 45.0, 55.0, 70.0);

        assertThat(claims()).isEqualTo(1);
        Map<String, Object> claim = jdbc.queryForMap("SELECT * FROM claims");
        assertThat(claim.get("status")).isEqualTo("UNDER_REVIEW");
        assertThat(claim.get("trigger_date").toString()).isEqualTo("2026-11-29");
        assertThat(claim.get("season_id")).isEqualTo(seasonId);
        assertThat(claim.get("amount")).isNull();       // money only from the insurer (question 4)
        assertThat(claim.get("payout_rate")).isNull();
        assertThat(claim.get("model_version")).isEqualTo("0.3.0");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE type = 'CLAIM' AND code = 'CLAIM_TRIGGERED'",
                Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action = 'CLAIM_TRIGGERED'", Long.class))
                .isEqualTo(1);
    }

    @Test
    void evidenceIsFrozenWithTheValuesAndThresholdsUsed() throws Exception {
        sendStatus("2026-11-15", "AT_RISK", 45.0, null, null);
        sendStatus("2026-11-29", "AT_RISK", 45.0, 55.0, 70.0);
        long id = jdbc.queryForObject("SELECT id FROM claims", Long.class);

        mvc.perform(get("/api/v1/claims/" + id).header("Authorization", "Bearer " + farmerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plotName").value("River field"))
                .andExpect(jsonPath("$.seasonCode").value("2026A"))
                .andExpect(jsonPath("$.amount").doesNotExist())
                .andExpect(jsonPath("$.decision").doesNotExist())
                .andExpect(jsonPath("$.evidenceSnapshot.atRiskRun.days").value(14))
                .andExpect(jsonPath("$.evidenceSnapshot.atRiskRun.validObservations").value(2))
                .andExpect(jsonPath("$.evidenceSnapshot.thresholds.trigger_at_risk_days.value").value(10))
                .andExpect(jsonPath("$.evidenceSnapshot.forecast.met").value(true))
                .andExpect(jsonPath("$.evidenceSnapshot.forecast.highPct").value(70))
                .andExpect(jsonPath("$.evidenceSnapshot.rain.met").value(true))
                .andExpect(jsonPath("$.evidenceSnapshot.rain.deficitPct").value(55))
                .andExpect(jsonPath("$.evidenceSnapshot.modelVersions[0]").value("0.3.0"))
                .andExpect(jsonPath("$.evidenceSnapshot.reviewFlags[0]").value("LOW_CONFIDENCE_FORECAST"))
                .andExpect(jsonPath("$.evidenceSnapshot.chart.observations.length()").value(2));
    }

    @Test
    void nothingTriggersWhileTheRuleValuesAreNotSupplied() throws Exception {
        setRule(null, 80, 40);
        sendStatus("2026-11-15", "AT_RISK", 10.0, 20.0, 30.0);
        sendStatus("2026-11-29", "AT_RISK", 10.0, 20.0, 30.0);
        assertThat(claims()).isZero();
    }

    @Test
    void anUninsuredPlotNeverGetsAClaim() throws Exception {
        jdbc.update("UPDATE enrollments SET status = 'PENDING'");
        sendStatus("2026-11-15", "AT_RISK", 10.0, 20.0, 30.0);
        sendStatus("2026-11-29", "AT_RISK", 10.0, 20.0, 30.0);
        assertThat(claims()).isZero();
    }

    @Test
    void missingDataBreaksTheAtRiskRun() throws Exception {
        sendStatus("2026-11-10", "AT_RISK", 10.0, null, null);
        sendStatus("2026-11-20", "NOT_ENOUGH_DATA", null, null, null);
        sendStatus("2026-11-25", "AT_RISK", 10.0, 20.0, 30.0);
        assertThat(claims()).isZero(); // the run restarted on 11-25, so it is 0 days long
    }

    @Test
    void stressBeforeTheSensitiveStageDoesNotCount() throws Exception {
        sendStatus("2026-10-20", "AT_RISK", 10.0, null, null);  // day 30
        sendStatus("2026-11-01", "AT_RISK", 10.0, 20.0, 30.0);  // day 42
        assertThat(claims()).isZero();
    }

    @Test
    void stressAloneIsNotEnoughWithoutALossSignal() throws Exception {
        sendStatus("2026-11-15", "AT_RISK", 90.0, null, null);
        sendStatus("2026-11-29", "AT_RISK", 90.0, 70.0, 95.0); // range not wholly below 80, rain deficit 10
        assertThat(claims()).isZero();
    }

    @Test
    void reRunningTheAnalysisNeverCreatesASecondClaim() throws Exception {
        sendStatus("2026-11-15", "AT_RISK", 45.0, null, null);
        sendStatus("2026-11-29", "AT_RISK", 45.0, 55.0, 70.0);
        sendStatus("2026-11-29", "AT_RISK", 45.0, 55.0, 70.0);
        sendStatus("2026-12-05", "AT_RISK", 40.0, 50.0, 65.0);
        assertThat(claims()).isEqualTo(1);
    }

    @Test
    void farmersSeeOnlyTheirOwnClaimsAndOfficersSeeAll() throws Exception {
        sendStatus("2026-11-15", "AT_RISK", 45.0, null, null);
        sendStatus("2026-11-29", "AT_RISK", 45.0, 55.0, 70.0);
        long id = jdbc.queryForObject("SELECT id FROM claims", Long.class);

        mvc.perform(get("/api/v1/claims").header("Authorization", "Bearer " + farmerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.items[0].evidenceSnapshot").doesNotExist());

        String other = farmerToken("+250785555552");
        mvc.perform(get("/api/v1/claims").header("Authorization", "Bearer " + other))
                .andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(get("/api/v1/claims/" + id).header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CLAIM_NOT_FOUND"));

        String officer = staffToken("+250785555553", "OFFICER", null);
        mvc.perform(get("/api/v1/claims/" + id).header("Authorization", "Bearer " + officer))
                .andExpect(status().isOk());

        String agent = staffToken("+250785555554", "AGENT", cooperativeId);
        mvc.perform(get("/api/v1/claims").header("Authorization", "Bearer " + agent))
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void thereIsNoWayToCreateAClaimThroughTheApi() throws Exception {
        mvc.perform(post("/api/v1/claims").header("Authorization", "Bearer " + farmerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(404, 405));
        assertThat(claims()).isZero();
    }
}
