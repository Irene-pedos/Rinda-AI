package com.rinda.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/** Phase 3: farmer problem reports, agent plot checks, the agent's farmer list and field visits. */
class FieldEvidenceApiTest extends ApiTestBase {

    private static final List<List<Double>> SQUARE = List.of(List.of(30.100, -1.950), List.of(30.102, -1.950),
            List.of(30.102, -1.948), List.of(30.100, -1.948), List.of(30.100, -1.950));
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F'};

    private String farmer;
    private String agent;
    private long plotId;

    @BeforeEach
    void farmerAgentAndPlot() throws Exception {
        farmer = farmerToken("+250788888881");
        agent = staffToken("+250788888882", "AGENT", cooperativeId);
        Map<String, Object> plot = Map.of("name", "Valley field", "plantingDate", "2026-09-20",
                "boundary", Map.of("type", "Polygon", "coordinates", List.of(SQUARE)));
        String body = mvc.perform(post("/api/v1/plots").header("Authorization", "Bearer " + farmer)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(plot)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        plotId = mapper.readTree(body).get("id").asLong();
    }

    private ResultActions report(String token, Map<String, String> params, MockMultipartFile photo) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/v1/problem-reports");
        params.forEach(request::param);
        if (photo != null) {
            request.file(photo);
        }
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private Map<String, String> dryLeaves() {
        return Map.of("plotId", String.valueOf(plotId), "reason", "DRY_LEAVES", "note", "Leaves curling since Monday");
    }

    private long openClaim(String status) {
        jdbc.update("UPDATE plots SET status = 'ACTIVE' WHERE id = ?", plotId);
        jdbc.update("INSERT INTO seasons (code, type) VALUES ('2026A', 'A')");
        long season = jdbc.queryForObject("SELECT id FROM seasons WHERE code = '2026A'", Long.class);
        jdbc.update("""
                INSERT INTO claims (plot_id, season_id, trigger_date, status, evidence_snapshot_json, model_version)
                VALUES (?, ?, '2026-11-29', ?, '{}', '0.3.0')
                """, plotId, season, status);
        return jdbc.queryForObject("SELECT id FROM claims", Long.class);
    }

    private ResultActions visit(Map<String, String> extra) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/v1/agent/field-visits");
        request.param("plotId", String.valueOf(plotId)).param("plantCondition", "SEVERE_LOSS")
                .param("observedCause", "WATER_DEFICIT").param("growthStage", "FLOWERING")
                .param("visitedAt", "2026-11-05T09:00:00Z").param("moistureChecked", "true");
        extra.forEach(request::param);
        return mvc.perform(request.header("Authorization", "Bearer " + agent));
    }

    // ===== Problem reports =====

    @Test
    void aReportIsEvidenceAndNeverStartsAClaim() throws Exception {
        report(farmer, dryLeaves(), null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.reason").value("DRY_LEAVES"))
                .andExpect(jsonPath("$.plotName").value("Valley field"))
                .andExpect(jsonPath("$.claimId").doesNotExist())
                .andExpect(jsonPath("$.photoUrl").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM claims", Long.class)).isZero();
    }

    @Test
    void aReportIsAttachedToTheOpenClaim() throws Exception {
        long claimId = openClaim("UNDER_REVIEW");
        report(farmer, dryLeaves(), null).andExpect(jsonPath("$.claimId").value(claimId));
        String officer = staffToken("+250788888883", "OFFICER", null);
        mvc.perform(get("/api/v1/problem-reports").param("claimId", String.valueOf(claimId))
                        .header("Authorization", "Bearer " + officer))
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void pestsAndDiseasesAreNotReasons() throws Exception {
        report(farmer, Map.of("plotId", String.valueOf(plotId), "reason", "FALL_ARMYWORM"), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REPORT_REASON_INVALID"));
    }

    @Test
    void anOutboxRetryWithTheSameClientIdIsStoredOnce() throws Exception {
        Map<String, String> params = Map.of("plotId", String.valueOf(plotId), "reason", "FLOODING",
                "clientId", "3f0c9e2a-outbox-1", "reportedAt", "2026-11-30T07:15:00Z");
        long first = mapper.readTree(report(farmer, params, null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        report(farmer, params, null).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(first));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM problem_reports", Long.class)).isEqualTo(1);
    }

    @Test
    void askingForAVisitTellsTheCooperativeAgent() throws Exception {
        report(farmer, Map.of("plotId", String.valueOf(plotId), "reason", "YELLOW_LEAVES",
                "agentVisitRequested", "true"), null).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE code = 'AGENT_VISIT_REQUESTED'",
                Long.class)).isEqualTo(1);
    }

    @Test
    void photosArePrivate() throws Exception {
        String body = report(farmer, dryLeaves(), new MockMultipartFile("photo", "leaf.jpg", "image/jpeg", JPEG))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String url = mapper.readTree(body).get("photoUrl").asText();

        mvc.perform(get(url).header("Authorization", "Bearer " + farmer))
                .andExpect(status().isOk()).andExpect(content().contentType("image/jpeg"))
                .andExpect(content().bytes(JPEG));
        mvc.perform(get(url).header("Authorization", "Bearer " + agent)).andExpect(status().isOk());
        String other = farmerToken("+250788888884");
        mvc.perform(get(url).header("Authorization", "Bearer " + other)).andExpect(status().isNotFound());
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
    }

    @Test
    void onlyRealImagesAreAccepted() throws Exception {
        report(farmer, dryLeaves(), new MockMultipartFile("photo", "x.jpg", "image/jpeg", "not an image".getBytes()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PHOTO_INVALID"));
    }

    @Test
    void farmersReportOnlyOnTheirOwnPlots() throws Exception {
        String other = farmerToken("+250788888885");
        report(other, dryLeaves(), null).andExpect(status().isNotFound());
        report(agent, dryLeaves(), null).andExpect(status().isForbidden());
    }

    // ===== Agent: farmers and plot checks =====

    @Test
    void agentSeesTheCooperativeFarmersWithWhatNeedsAttention() throws Exception {
        mvc.perform(get("/api/v1/agent/farmers").header("Authorization", "Bearer " + agent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].plotsCount").value(1))
                .andExpect(jsonPath("$[0].pendingVerificationCount").value(1));
        mvc.perform(get("/api/v1/agent/farmers").header("Authorization", "Bearer " + farmer))
                .andExpect(status().isForbidden());
    }

    @Test
    void agentVerifiesAPlotAndCanCorrectThePlantingDate() throws Exception {
        mvc.perform(post("/api/v1/agent/plots/" + plotId + "/verify").header("Authorization", "Bearer " + agent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("decision", "ACTIVE",
                                "confirmedPlantingDate", "2026-09-25"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.plantingDate").value("2026-09-25"));
        assertThat(jdbc.queryForObject("SELECT verified_by IS NOT NULL FROM plots WHERE id = ?", Boolean.class, plotId))
                .isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action = 'PLOT_VERIFIED'", Long.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE code = 'PLOT_VERIFIED'", Long.class))
                .isEqualTo(1);

        mvc.perform(post("/api/v1/agent/plots/" + plotId + "/verify").header("Authorization", "Bearer " + agent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("decision", "REJECTED", "rejectionReason", "x"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLOT_NOT_PENDING"));
    }

    @Test
    void aRejectionNeedsAReasonForTheFarmer() throws Exception {
        mvc.perform(post("/api/v1/agent/plots/" + plotId + "/verify").header("Authorization", "Bearer " + agent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("decision", "REJECTED"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REJECTION_REASON_REQUIRED"));
        mvc.perform(post("/api/v1/agent/plots/" + plotId + "/verify").header("Authorization", "Bearer " + agent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("decision", "REJECTED",
                                "rejectionReason", "The boundary includes the neighbour's field."))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        assertThat(jdbc.queryForObject("SELECT rejection_reason FROM plots WHERE id = ?", String.class, plotId))
                .isEqualTo("The boundary includes the neighbour's field.");
    }

    @Test
    void anAgentOfAnotherCooperativeCannotCheckThePlot() throws Exception {
        String outsider = staffToken("+250788888886", "AGENT", newCooperative("Other Cooperative"));
        mvc.perform(post("/api/v1/agent/plots/" + plotId + "/verify").header("Authorization", "Bearer " + outsider)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("decision", "ACTIVE"))))
                .andExpect(status().isNotFound());
    }

    // ===== Field visits =====

    @Test
    void aFieldVisitIsEvidenceForTheClaimAndTellsTheOfficer() throws Exception {
        String officer = staffToken("+250788888887", "OFFICER", null);
        long claimId = openClaim("FIELD_VISIT_REQUESTED");
        visit(Map.of("lat", "-1.949", "lng", "30.101", "notes", "Most plants wilted")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.claimId").value(claimId))
                .andExpect(jsonPath("$.checklist.moistureChecked").value(true));
        assertThat(jdbc.queryForObject("SELECT status FROM claims", String.class)).isEqualTo("FIELD_VISIT_REQUESTED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE code = 'FIELD_VISIT_RECORDED'",
                Long.class)).isEqualTo(1);
        mvc.perform(get("/api/v1/field-visits").param("claimId", String.valueOf(claimId))
                        .header("Authorization", "Bearer " + officer))
                .andExpect(jsonPath("$.items[0].plantCondition").value("SEVERE_LOSS"));
    }

    @Test
    void aFieldVisitResentFromTheOutboxIsStoredOnce() throws Exception {
        visit(Map.of("clientId", "visit-1")).andExpect(status().isCreated());
        visit(Map.of("clientId", "visit-1")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM field_visits", Long.class)).isEqualTo(1);
    }

    @Test
    void fieldVisitValuesAreChecked() throws Exception {
        visit(Map.of("growthStage", "HARVESTED")).andExpect(status().isBadRequest());
        visit(Map.of("lat", "-1.949")).andExpect(status().isBadRequest()); // lat without lng
    }

    @Test
    void farmersCannotSeeFieldVisits() throws Exception {
        mvc.perform(get("/api/v1/field-visits").header("Authorization", "Bearer " + farmer))
                .andExpect(status().isForbidden());
    }
}
