package com.rinda.backend;

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
                Map.of("dapBin", 20, "p10", 0.3, "p50", 0.4, "p90", 0.5),
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
                .andExpect(jsonPath("$.bins[1].p50").value(0.4));
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

    @Test
    void anotherFarmerGetsTheSame404AsAMissingPlot() throws Exception {
        String other = farmerToken("+250785555552");

        for (String path : List.of("/observations", "/weather", "/baseline")) {
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
