package com.rinda.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

/** "Type the UPI, see the parcel" on the MOCK registry (fake zero-code UPIs only). */
@TestPropertySource(properties = "rinda.plots.land-registry-provider=MOCK")
class LandRegistryApiTest extends ApiTestBase {

    private String farmer;

    @BeforeEach
    void farmer() throws Exception {
        farmer = farmerToken("+250789997771");
    }

    private ResultActions lookup(String token, String upi) throws Exception {
        return mvc.perform(get("/api/v1/land-registry/parcels").param("upi", upi)
                .header("Authorization", "Bearer " + token));
    }

    @Test
    void aMockUpiShowsTheParcelLabelledAsDemoData() throws Exception {
        String body = lookup(farmer, "0/00/00/00/1002").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode result = mapper.readTree(body);

        assertThat(result.get("found").asBoolean()).isTrue();
        assertThat(result.get("source").asText()).isEqualTo("MOCK");
        assertThat(result.get("isMock").asBoolean()).isTrue();
        assertThat(result.get("notice").asText()).contains("not official").contains("walking its edge");
        assertThat(result.at("/parcel/boundary/type").asText()).isEqualTo("Polygon");
        assertThat(result.at("/parcel/boundary/coordinates/0")).hasSize(6);
        assertThat(result.at("/parcel/areaM2").asDouble()).isBetween(23_000.0, 25_000.0);
        assertThat(result.at("/parcel/areaHa").asDouble())
                .isCloseTo(result.at("/parcel/areaM2").asDouble() / 10_000, org.assertj.core.data.Offset.offset(0.001));
        assertThat(result.at("/parcel/location").asText()).startsWith("Demo");
        // A UPI lookup never reveals who owns the parcel.
        assertThat(body).doesNotContainIgnoringCase("owner");
    }

    @Test
    void aRealLookingUpiIsNeverMatchedOrGivenAnInventedShape() throws Exception {
        lookup(farmer, "1/01/01/01/0001").andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(false))
                .andExpect(jsonPath("$.parcel").doesNotExist())
                .andExpect(jsonPath("$.notice").value(org.hamcrest.Matchers.containsString("You can still register")));
    }

    @Test
    void anUnknownFakeUpiIsNotFound() throws Exception {
        lookup(farmer, "0/00/00/00/5555").andExpect(status().isOk()).andExpect(jsonPath("$.found").value(false));
    }

    @Test
    void aMalformedUpiIsRefused() throws Exception {
        lookup(farmer, "not-a-upi").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UPI_INVALID"));
    }

    @Test
    void theLookupNeedsALogin() throws Exception {
        mvc.perform(get("/api/v1/land-registry/parcels").param("upi", "0/00/00/00/1001"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mockDataNeverRaisesTheVerificationLevel() throws Exception {
        setThreshold("walk_max_accuracy_m", 10);
        setThreshold("plot_area_mismatch_pct", 15);
        String planted = LocalDate.now().minusDays(10).toString();
        // A parcel the mock knows, walked: still SELF_DECLARED until the farmer's own document is uploaded.
        long id = mapper.readTree(send(farmer, org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/plots"), registration("Mock parcel", planted, "0/00/00/00/1001"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        String walked = walk(farmer, id, squareFor(id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationLevel").value("SELF_DECLARED"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();

        uploadETitle(farmer, id, mapper.readTree(walked).get("walkedAreaM2").decimalValue())
                .andExpect(status().isCreated());
        mvc.perform(get("/api/v1/plots/" + id).header("Authorization", "Bearer " + farmer))
                .andExpect(jsonPath("$.verificationLevel").value("DOCUMENT_SUPPORTED"))
                .andExpect(jsonPath("$.financialActionsAllowed").value(false));
    }
}
