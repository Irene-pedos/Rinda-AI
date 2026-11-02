package com.rinda.backend;

import static org.assertj.core.api.Assertions.assertThat;
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

class MlResultApiTest extends ApiTestBase {

    private static final String KEY = "test-only-key-test-only-key-test-only-key";
    private static final List<List<Double>> SQUARE = List.of(List.of(30.100, -1.950), List.of(30.102, -1.950),
            List.of(30.102, -1.948), List.of(30.100, -1.948), List.of(30.100, -1.950));

    private long plotId;

    @BeforeEach
    void createPlot() throws Exception {
        String token = farmerToken("+250784444441");
        Map<String, Object> plot = Map.of("name", "Field", "plantingDate", "2026-09-20",
                "boundary", Map.of("type", "Polygon", "coordinates", List.of(SQUARE)));
        String body = mvc.perform(post("/api/v1/plots").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(plot)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        plotId = mapper.readTree(body).get("id").asLong();
    }

    private Map<String, Object> result(String origin) {
        Map<String, Object> result = new HashMap<>();
        result.put("plotId", plotId);
        result.put("dataOrigin", origin);
        result.put("modelVersion", "0.1.0");
        result.put("plantingDate", "2026-09-20");
        result.put("inputFrom", "2026-09-20");
        result.put("inputTo", "2026-10-20");
        result.put("params", Map.of("minClearPixels", 20));
        result.put("observations", List.of(
                Map.of("date", "2026-10-01", "source", "S2", "ndvi", 0.41, "smoothedNdvi", 0.40,
                        "clearPixels", 240, "cloudPct", 12.5),
                Map.of("date", "2026-10-06", "source", "S2", "clearPixels", 3)));
        result.put("weather", List.of(Map.of("date", "2026-10-01", "rainMm", 4.2)));
        result.put("baseline", Map.of("seasonType", "A", "version", "plot-history-v1", "bins",
                List.of(Map.of("dapBin", 10, "p10", 0.2, "p50", 0.3, "p90", 0.4))));
        return result;
    }

    private ResultActions send(Object body, String key) throws Exception {
        var request = post("/api/v1/internal/ml/results").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body));
        if (key != null) {
            request.header("X-Internal-Api-Key", key);
        }
        return mvc.perform(request);
    }

    private void makeActive() {
        jdbc.update("UPDATE plots SET status = 'ACTIVE' WHERE id = ?", plotId);
    }

    private void makeDemo() {
        jdbc.update("UPDATE plots SET is_demo = TRUE WHERE id = ?", plotId);
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    @Test
    void realResultIsStoredWithItsOriginAndRunDetails() throws Exception {
        makeActive();
        send(result("GEE"), KEY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.observations").value(2))
                .andExpect(jsonPath("$.weatherDays").value(1))
                .andExpect(jsonPath("$.baselineBins").value(1));

        assertThat(jdbc.queryForObject("SELECT DISTINCT data_origin FROM observations", String.class)).isEqualTo("GEE");
        assertThat(jdbc.queryForObject("SELECT data_origin FROM weather_daily", String.class)).isEqualTo("GEE");
        assertThat(jdbc.queryForObject("SELECT data_origin FROM baselines", String.class)).isEqualTo("GEE");
        assertThat(jdbc.queryForObject("SELECT model_version FROM ml_runs", String.class)).isEqualTo("0.1.0");
        assertThat(jdbc.queryForObject("SELECT params_json->>'$.minClearPixels' FROM ml_runs", String.class)).isEqualTo("20");
    }

    @Test
    void cloudyDateIsStoredWithoutAnNdviValue() throws Exception {
        makeActive();
        send(result("GEE"), KEY).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM observations WHERE ndvi IS NULL AND clear_pixels = 3", Long.class))
                .isEqualTo(1);
    }

    @Test
    void sendingTheSameResultAgainChangesNoData() throws Exception {
        makeActive();
        send(result("GEE"), KEY).andExpect(status().isOk());
        send(result("GEE"), KEY).andExpect(status().isOk());

        assertThat(count("observations")).isEqualTo(2);
        assertThat(count("weather_daily")).isEqualTo(1);
        assertThat(count("baselines")).isEqualTo(1);
    }

    @Test
    void sampleDataIsRefusedOnARealPlot() throws Exception {
        makeActive();
        send(result("SAMPLE_FILE"), KEY)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SAMPLE_ON_REAL_PLOT"));
        assertThat(count("observations")).isZero();
        assertThat(count("ml_runs")).isZero();
    }

    @Test
    void sampleDataIsAcceptedOnADemoPlotAndLabelled() throws Exception {
        makeDemo();
        send(result("SAMPLE_FILE"), KEY).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT DISTINCT data_origin FROM observations", String.class))
                .isEqualTo("SAMPLE_FILE");
    }

    @Test
    void realDataIsRefusedOnAnUnconfirmedPlot() throws Exception {
        makeActive();
        jdbc.update("UPDATE plots SET data_status = 'TO_CONFIRM' WHERE id = ?", plotId);
        send(result("GEE"), KEY)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLOT_NOT_CONFIRMED"));
    }

    @Test
    void realDataIsRefusedOnAnUnverifiedPlot() throws Exception {
        send(result("GEE"), KEY)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLOT_NOT_ACTIVE"));
    }

    @Test
    void wrongOrMissingKeyIsRejected() throws Exception {
        makeActive();
        send(result("GEE"), null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INTERNAL_KEY_INVALID"));
        send(result("GEE"), "wrong").andExpect(status().isUnauthorized());
        assertThat(count("observations")).isZero();
    }

    @Test
    void userTokenCannotReplaceTheKey() throws Exception {
        makeActive();
        String officer = staffToken("+250784444442", "OFFICER", null);
        mvc.perform(post("/api/v1/internal/ml/results").header("Authorization", "Bearer " + officer)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(result("GEE"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownPlotIsRejected() throws Exception {
        Map<String, Object> body = result("GEE");
        body.put("plotId", 999999);
        send(body, KEY).andExpect(status().isNotFound());
    }

    @Test
    void outOfRangeNdviIsRejected() throws Exception {
        makeActive();
        Map<String, Object> body = result("GEE");
        body.put("observations", List.of(Map.of("date", "2026-10-01", "source", "S2", "ndvi", 1.5)));
        send(body, KEY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void baselineMustRiseFromP10ToP90() throws Exception {
        makeActive();
        Map<String, Object> body = result("GEE");
        body.put("baseline", Map.of("seasonType", "A", "version", "v1", "bins",
                List.of(Map.of("dapBin", 10, "p10", 0.5, "p50", 0.3, "p90", 0.4))));
        send(body, KEY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BASELINE_INVALID"));
    }
}
