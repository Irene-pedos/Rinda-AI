package com.rinda.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.rinda.app.data.model.ApiProblem;
import com.rinda.app.data.model.BaselineResponse;
import com.rinda.app.data.model.ObservationResponse;
import com.rinda.app.data.model.PlotResponse;
import com.rinda.app.data.model.PlotStatus;
import com.rinda.app.data.model.Role;
import com.rinda.app.data.model.TokenResponse;
import org.junit.Test;

public class ApiClientAndDtoTest {

    private final Gson gson = new Gson();

    @Test
    public void testTokenResponseDeserialization() {
        String json = "{\n" +
                "  \"accessToken\": \"eyJ...\",\n" +
                "  \"accessTokenExpiresIn\": 900,\n" +
                "  \"refreshToken\": \"kX2...\",\n" +
                "  \"user\": {\n" +
                "    \"id\": 9001,\n" +
                "    \"phone\": \"+250780000001\",\n" +
                "    \"fullName\": \"Demo Farmer One\",\n" +
                "    \"role\": \"FARMER\",\n" +
                "    \"language\": \"en\",\n" +
                "    \"isDemo\": true\n" +
                "  }\n" +
                "}";

        TokenResponse token = gson.fromJson(json, TokenResponse.class);
        assertNotNull(token);
        assertEquals("eyJ...", token.getAccessToken());
        assertEquals(900, token.getAccessTokenExpiresIn());
        assertNotNull(token.getUser());
        assertEquals(9001L, token.getUser().getId());
        assertEquals(Role.FARMER, token.getUser().getRole());
        assertTrue(token.getUser().isDemo());
    }

    @Test
    public void testPlotResponseDeserialization() {
        String json = "{\n" +
                "  \"id\": 9001,\n" +
                "  \"farmerId\": 9001,\n" +
                "  \"name\": \"Demo plot 1\",\n" +
                "  \"crop\": \"MAIZE\",\n" +
                "  \"boundary\": {\n" +
                "    \"type\": \"Polygon\",\n" +
                "    \"coordinates\": [[[30.1, -1.95], [30.102, -1.95], [30.102, -1.948], [30.1, -1.948], [30.1, -1.95]]]\n" +
                "  },\n" +
                "  \"areaHa\": 4.9209,\n" +
                "  \"plantingDate\": null,\n" +
                "  \"status\": \"ACTIVE\",\n" +
                "  \"dataStatus\": \"TO_CONFIRM\",\n" +
                "  \"isDemo\": true,\n" +
                "  \"createdAt\": \"2026-11-02T13:34:07.608495Z\",\n" +
                "  \"updatedAt\": \"2026-11-02T13:34:07.608495Z\"\n" +
                "}";

        PlotResponse plot = gson.fromJson(json, PlotResponse.class);
        assertNotNull(plot);
        assertEquals(9001L, plot.getId());
        assertEquals("Demo plot 1", plot.getName());
        assertEquals(PlotStatus.ACTIVE, plot.getStatus());
        assertEquals("TO_CONFIRM", plot.getDataStatus());
        assertTrue(plot.isDemo());
        assertNotNull(plot.getBoundary());
        assertEquals(1, plot.getBoundary().getCoordinates().size());
        assertEquals(5, plot.getBoundary().getCoordinates().get(0).size());
    }

    @Test
    public void testObservationsAndBaselineDeserialization() {
        String obsJson = "[\n" +
                "  {\n" +
                "    \"date\": \"2026-10-05\",\n" +
                "    \"dap\": 15,\n" +
                "    \"source\": \"S2\",\n" +
                "    \"ndvi\": 0.41,\n" +
                "    \"smoothedNdvi\": 0.4,\n" +
                "    \"clearPixels\": 240,\n" +
                "    \"cloudPct\": 12.5,\n" +
                "    \"dataOrigin\": \"GEE\"\n" +
                "  }\n" +
                "]";

        ObservationResponse[] obs = gson.fromJson(obsJson, ObservationResponse[].class);
        assertEquals(1, obs.length);
        assertEquals("2026-10-05", obs[0].getDate());
        assertEquals(Integer.valueOf(15), obs[0].getDap());
        assertEquals("S2", obs[0].getSource());

        String baselineJson = "{\n" +
                "  \"seasonType\": \"A\",\n" +
                "  \"version\": \"plot-history-v1\",\n" +
                "  \"binDays\": 10,\n" +
                "  \"dataOrigin\": \"GEE\",\n" +
                "  \"bins\": [\n" +
                "    { \"dapBin\": 10, \"p10\": 0.2, \"p50\": 0.3, \"p90\": 0.4 }\n" +
                "  ]\n" +
                "}";

        BaselineResponse baseline = gson.fromJson(baselineJson, BaselineResponse.class);
        assertNotNull(baseline);
        assertEquals("A", baseline.getSeasonType());
        assertEquals(Integer.valueOf(10), baseline.getBinDays());
        assertEquals(1, baseline.getBins().size());
        assertEquals(10, baseline.getBins().get(0).getDapBin());
    }

    @Test
    public void testProblemDetailDeserialization() {
        String errorJson = "{\n" +
                "  \"type\": \"about:blank\",\n" +
                "  \"title\": \"Bad Request\",\n" +
                "  \"status\": 400,\n" +
                "  \"detail\": \"The boundary must be inside Rwanda.\",\n" +
                "  \"instance\": \"/api/v1/plots\",\n" +
                "  \"code\": \"PLOT_OUTSIDE_RWANDA\"\n" +
                "}";

        ApiProblem problem = gson.fromJson(errorJson, ApiProblem.class);
        assertNotNull(problem);
        assertEquals("PLOT_OUTSIDE_RWANDA", problem.getCode());
        assertEquals("The boundary must be inside Rwanda.", problem.getDetail());
        assertEquals(Integer.valueOf(400), problem.getStatus());
    }
}
