package com.rinda.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
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

class NotificationApiTest extends ApiTestBase {

    private static final String KEY = "test-only-key-test-only-key-test-only-key";
    private static final String PLANTED = "2026-09-20";
    private static final List<List<Double>> SQUARE = List.of(List.of(30.100, -1.950), List.of(30.102, -1.950),
            List.of(30.102, -1.948), List.of(30.100, -1.948), List.of(30.100, -1.950));

    private String farmer;
    private long plotId;

    @BeforeEach
    void createPlot() throws Exception {
        farmer = farmerToken("+250786666661");
        plotId = pendingPlot(farmer, "Hillside", PLANTED);
        jdbc.update("UPDATE plots SET status = 'ACTIVE', verification_level = 'FIELD_VERIFIED' WHERE id = ?", plotId);
    }

    private void health(String date, String status, String cause) throws Exception {
        Map<String, Object> statusBody = new HashMap<>();
        statusBody.put("statusDate", date);
        statusBody.put("status", status);
        statusBody.put("cause", cause);
        statusBody.put("confidence", "MEDIUM");
        statusBody.put("explanation", Map.of("signals", List.of()));
        statusBody.put("thresholdsUsed", Map.of());
        Map<String, Object> result = new HashMap<>();
        result.put("plotId", plotId);
        result.put("dataOrigin", "GEE");
        result.put("modelVersion", "0.5.0");
        result.put("plantingDate", PLANTED);
        result.put("inputFrom", PLANTED);
        result.put("inputTo", date);
        result.put("params", Map.of());
        result.put("observations", List.of());
        result.put("weather", List.of());
        result.put("status", statusBody);
        mvc.perform(post("/api/v1/internal/ml/results").header("X-Internal-Api-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(result)))
                .andExpect(status().isOk());
    }

    private ResultActions list(String token) throws Exception {
        return mvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer " + token));
    }

    private long count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notifications", Long.class);
    }

    @Test
    void healthyPlotGetsNoAlert() throws Exception {
        health("2026-10-01", "HEALTHY", null);
        assertThat(count()).isZero();
    }

    @Test
    void atRiskAlertsTheFarmerAndOpensThePlotNotAClaim() throws Exception {
        health("2026-10-01", "HEALTHY", null);
        health("2026-10-10", "AT_RISK", "WATER_DEFICIT");

        list(farmer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].type").value("HEALTH"))
                .andExpect(jsonPath("$.items[0].code").value("HEALTH_AT_RISK"))
                .andExpect(jsonPath("$.items[0].plotId").value(plotId))
                .andExpect(jsonPath("$.items[0].plotName").value("Hillside"))
                .andExpect(jsonPath("$.items[0].link").value("/plots/" + plotId))
                .andExpect(jsonPath("$.items[0].isRead").value(false))
                .andExpect(jsonPath("$.items[0].isDemo").value(false))
                .andExpect(jsonPath("$.items[0].body").value(
                        "Hillside has been growing well below its normal range. This is a crop health alert, not a claim."));
    }

    @Test
    void lowRainAloneSaysSo() throws Exception {
        health("2026-10-01", "WATCH", null);

        list(farmer)
                .andExpect(jsonPath("$.items[0].code").value("HEALTH_WATCH"))
                .andExpect(jsonPath("$.items[0].body").value(
                        "Rain at Hillside has been below normal. This is a crop health alert, not a claim."));
    }

    @Test
    void onlyGettingWorseAlerts() throws Exception {
        health("2026-10-01", "WATCH", "UNEXPLAINED");
        health("2026-10-05", "WATCH", "UNEXPLAINED");
        health("2026-10-10", "AT_RISK", "UNEXPLAINED");
        health("2026-10-15", "WATCH", "UNEXPLAINED");
        health("2026-10-20", "HEALTHY", null);

        assertThat(jdbc.queryForList("SELECT code FROM notifications ORDER BY id", String.class))
                .containsExactly("HEALTH_WATCH", "HEALTH_AT_RISK");
    }

    @Test
    void notEnoughDataInBetweenDoesNotRepeatAnAlert() throws Exception {
        health("2026-10-01", "AT_RISK", "UNEXPLAINED");
        health("2026-10-05", "NOT_ENOUGH_DATA", null);
        health("2026-10-10", "AT_RISK", "UNEXPLAINED");

        assertThat(count()).isEqualTo(1);
    }

    @Test
    void reRunningTheSameDateDoesNotRepeatAnAlert() throws Exception {
        health("2026-10-10", "AT_RISK", "UNEXPLAINED");
        health("2026-10-10", "AT_RISK", "UNEXPLAINED");

        assertThat(count()).isEqualTo(1);
    }

    @Test
    void anOlderResultArrivingLateDoesNotAlert() throws Exception {
        health("2026-10-20", "HEALTHY", null);
        health("2026-10-10", "AT_RISK", "UNEXPLAINED");

        assertThat(count()).isZero();
    }

    @Test
    void worseningAgainAfterARecoveryAlertsAgain() throws Exception {
        health("2026-10-01", "WATCH", "UNEXPLAINED");
        health("2026-10-10", "HEALTHY", null);
        health("2026-10-20", "WATCH", "UNEXPLAINED");

        assertThat(count()).isEqualTo(2);
    }

    @Test
    void alertOnADemoPlotIsMarkedDemo() throws Exception {
        jdbc.update("UPDATE plots SET is_demo = TRUE WHERE id = ?", plotId);
        health("2026-10-01", "AT_RISK", "UNEXPLAINED");

        list(farmer).andExpect(jsonPath("$.items[0].isDemo").value(true));
    }

    @Test
    void newestFirstAndPaged() throws Exception {
        health("2026-10-01", "WATCH", "UNEXPLAINED");
        health("2026-10-10", "AT_RISK", "UNEXPLAINED");

        mvc.perform(get("/api/v1/notifications?size=1").header("Authorization", "Bearer " + farmer))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].code").value("HEALTH_AT_RISK"))
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/api/v1/notifications?size=0").header("Authorization", "Bearer " + farmer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAGE_INVALID"));
    }

    @Test
    void markingReadWorksTwiceAndOnlyForTheOwner() throws Exception {
        health("2026-10-01", "AT_RISK", "UNEXPLAINED");
        long id = jdbc.queryForObject("SELECT id FROM notifications", Long.class);

        mvc.perform(post("/api/v1/notifications/" + id + "/read").header("Authorization", "Bearer " + farmer))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/notifications/" + id + "/read").header("Authorization", "Bearer " + farmer))
                .andExpect(status().isNoContent());
        list(farmer).andExpect(jsonPath("$.items[0].isRead").value(true));

        String other = farmerToken("+250786666662");
        mvc.perform(post("/api/v1/notifications/" + id + "/read").header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
        list(other).andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void aNewModelVersionIsAuditedOnce() throws Exception {
        long before = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE action = 'MODEL_VERSION_ADDED' AND after_json->>'$.version' = '0.5.0'",
                Long.class);
        health("2026-10-01", "HEALTHY", null);
        health("2026-10-05", "HEALTHY", null);

        long after = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE action = 'MODEL_VERSION_ADDED' AND after_json->>'$.version' = '0.5.0'",
                Long.class);
        assertThat(after - before).isLessThanOrEqualTo(1);
        assertThat(after).isEqualTo(1);
    }

    @Test
    void loginIsRequired() throws Exception {
        mvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
    }
}
