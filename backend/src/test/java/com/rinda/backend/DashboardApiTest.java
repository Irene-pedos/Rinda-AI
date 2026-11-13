package com.rinda.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Phase 6: the officer dashboard (queue, case file, summary) and admin settings. */
class DashboardApiTest extends ApiTestBase {

    private static final List<List<Double>> SQUARE = List.of(List.of(30.100, -1.950), List.of(30.102, -1.950),
            List.of(30.102, -1.948), List.of(30.100, -1.948), List.of(30.100, -1.950));

    private String farmer;
    private String officer;
    private String admin;
    private long plotId;
    private long seasonId;

    @BeforeEach
    void setUp() throws Exception {
        farmer = farmerToken("+250781111101");
        officer = staffToken("+250781111102", "OFFICER", null);
        admin = staffToken("+250781111103", "ADMIN", null);
        plotId = plot("Upper field");
        jdbc.update("UPDATE plots SET status = 'ACTIVE', verification_level = 'FIELD_VERIFIED' WHERE id = ?", plotId);
        jdbc.update("INSERT INTO seasons (code, type) VALUES ('T2026A', 'A')");
        seasonId = jdbc.queryForObject("SELECT id FROM seasons WHERE code = 'T2026A'", Long.class);
    }

    /** thresholds is shared between test classes: put back what these tests change. */
    @AfterEach
    void restoreThresholds() {
        jdbc.update("""
                UPDATE thresholds SET threshold_value = NULL, status = 'TO_CONFIRM', updated_by = NULL
                WHERE threshold_key = 'trigger_at_risk_days'
                """);
    }

    private long plot(String name) throws Exception {
        return pendingPlot(farmer, name, "2026-09-20");
    }

    private long claim(long plot, long season, String status, String evidence) {
        jdbc.update("""
                INSERT INTO claims (plot_id, season_id, trigger_date, status, evidence_snapshot_json, model_version)
                VALUES (?, ?, '2026-11-01', ?, ?, '0.3.0')
                """, plot, season, status, evidence);
        return jdbc.queryForObject("SELECT MAX(id) FROM claims", Long.class);
    }

    private ResultActions as(String token, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder r,
                             Object body) throws Exception {
        r.header("Authorization", "Bearer " + token);
        if (body != null) {
            r.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
        }
        return mvc.perform(r);
    }

    // ===== Officer queue =====

    @Test
    void theQueuePutsReviewsFirstAndFilters() throws Exception {
        long underReview = claim(plotId, seasonId, "UNDER_REVIEW", "{\"reviewFlags\": [\"LOW_CONFIDENCE_STATUS\"]}");
        long review = claim(plot("Lower field"), seasonId, "REVIEW_REQUESTED", "{\"reviewFlags\": []}");
        claim(plot("Old field"), seasonId, "PAID", "{}");

        as(officer, get("/api/v1/officer/claims"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.items[0].id").value(review))
                .andExpect(jsonPath("$.items[1].id").value(underReview))
                .andExpect(jsonPath("$.items[1].farmerName").value("Test Farmer"))
                .andExpect(jsonPath("$.items[1].cooperativeName").value("Test Cooperative"))
                .andExpect(jsonPath("$.items[1].daysOpen").isNumber())
                .andExpect(jsonPath("$.items[2].daysOpen").doesNotExist());

        as(officer, get("/api/v1/officer/claims").param("status", "UNDER_REVIEW,REVIEW_REQUESTED"), null)
                .andExpect(jsonPath("$.totalItems").value(2));
        as(officer, get("/api/v1/officer/claims").param("flag", "LOW_CONFIDENCE_STATUS"), null)
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].reviewFlags[0]").value("LOW_CONFIDENCE_STATUS"));
        as(officer, get("/api/v1/officer/claims").param("q", "Lower"), null)
                .andExpect(jsonPath("$.totalItems").value(1));
        as(officer, get("/api/v1/officer/claims").param("status", "MAYBE"), null)
                .andExpect(status().isBadRequest());
        as(farmer, get("/api/v1/officer/claims"), null).andExpect(status().isForbidden());
    }

    @Test
    void theCaseFileHasEverythingForTheDecision() throws Exception {
        jdbc.update("INSERT INTO enrollments (plot_id, season_id, status, sum_insured) VALUES (?, ?, 'COVERED', 200000)",
                plotId, seasonId);
        long id = claim(plotId, seasonId, "UNDER_REVIEW", "{\"reviewFlags\": []}");
        jdbc.update("INSERT INTO payout_accounts (user_id, provider, msisdn, verified) "
                + "SELECT farmer_id, 'MTN_MOMO', '+250782222222', TRUE FROM plots WHERE id = ?", plotId);
        as(officer, post("/api/v1/claims/" + id + "/decision"), Map.of("decision", "DECLINED", "reason", "Rain was normal."))
                .andExpect(status().isOk());

        as(officer, get("/api/v1/officer/claims/" + id), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.claim.status").value("DECLINED"))
                .andExpect(jsonPath("$.farmer.cooperativeName").value("Test Cooperative"))
                .andExpect(jsonPath("$.plot.name").value("Upper field"))
                .andExpect(jsonPath("$.cover.status").value("COVERED"))
                .andExpect(jsonPath("$.cover.sumInsured").value(200000.0))
                .andExpect(jsonPath("$.payoutAccount.verified").value(true))
                .andExpect(jsonPath("$.payoutAccount.msisdnMasked").value("**********222"))
                .andExpect(jsonPath("$.decisions[0].reason").value("Rain was normal."))
                .andExpect(jsonPath("$.decisions[0].officerName").value("Test Staff"))
                .andExpect(jsonPath("$.history[0].action").value("CLAIM_DECIDED"))
                .andExpect(jsonPath("$.problemReports").isArray())
                .andExpect(jsonPath("$.fieldVisits").isArray());
    }

    @Test
    void theSummarySaysWhatIsWaitingAndWhichTriggerValuesAreMissing() throws Exception {
        claim(plotId, seasonId, "UNDER_REVIEW", "{\"reviewFlags\": [\"SAMPLE_DATA\"]}");
        as(officer, get("/api/v1/officer/summary"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.claimsByStatus.UNDER_REVIEW").value(1))
                .andExpect(jsonPath("$.awaitingDecision").value(1))
                .andExpect(jsonPath("$.flaggedOpen").value(1))
                .andExpect(jsonPath("$.triggerValuesMissing.length()").value(3))
                .andExpect(jsonPath("$.plotsByStatus.ACTIVE").value(1));
    }

    // ===== Thresholds =====

    @Test
    void adminSetsATriggerValueAndItIsAudited() throws Exception {
        as(admin, put("/api/v1/admin/thresholds/trigger_at_risk_days"), Map.of("value", 10, "status", "CONFIRMED_FOR_DEMO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(10.0))
                .andExpect(jsonPath("$.status").value("CONFIRMED_FOR_DEMO"))
                .andExpect(jsonPath("$.updatedByName").value("Test Staff"));
        as(admin, get("/api/v1/admin/audit-log").param("action", "THRESHOLD_CHANGED"), null)
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].before.value").doesNotExist())
                .andExpect(jsonPath("$.items[0].after.value").value(10.0));
        as(officer, get("/api/v1/officer/summary"), null)
                .andExpect(jsonPath("$.triggerValuesMissing.length()").value(2));
    }

    @Test
    void thresholdValuesAreChecked() throws Exception {
        as(admin, put("/api/v1/admin/thresholds/plot_min_area_ha"), Map.of("value", -1, "status", "CONFIRMED"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("THRESHOLD_INVALID"));
        Map<String, Object> noValue = new HashMap<>();
        noValue.put("value", null);
        noValue.put("status", "CONFIRMED");
        as(admin, put("/api/v1/admin/thresholds/trigger_at_risk_days"), noValue)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("THRESHOLD_INVALID"));
        as(admin, put("/api/v1/admin/thresholds/made_up"), Map.of("value", 1, "status", "CONFIRMED"))
                .andExpect(status().isNotFound());
        as(officer, put("/api/v1/admin/thresholds/trigger_at_risk_days"), Map.of("value", 5, "status", "CONFIRMED"))
                .andExpect(status().isForbidden());
    }

    // ===== Seasons =====

    @Test
    void seasonsAreCreatedAndConfirmedWithDates() throws Exception {
        as(admin, post("/api/v1/admin/seasons"), Map.of("code", "2027A", "type", "A", "dataStatus", "CONFIRMED"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SEASON_DATES_REQUIRED"));
        String body = as(admin, post("/api/v1/admin/seasons"), Map.of("code", "2027A", "type", "A", "dataStatus", "TO_CONFIRM"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = mapper.readTree(body).get("id").asLong();
        as(admin, post("/api/v1/admin/seasons"), Map.of("code", "2027A", "type", "B", "dataStatus", "TO_CONFIRM"))
                .andExpect(status().isConflict());
        as(admin, put("/api/v1/admin/seasons/" + id), Map.of("code", "2027A", "type", "A", "dataStatus", "CONFIRMED",
                "startDate", "2026-09-01", "endDate", "2027-01-31"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dataStatus").value("CONFIRMED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action IN ('SEASON_CREATED', 'SEASON_CHANGED')",
                Long.class)).isEqualTo(2);
    }

    // ===== Cover and payout rate =====

    @Test
    void onlyAVerifiedPlotCanBeCoveredOncePerSeason() throws Exception {
        long pending = plot("Pending field");
        as(admin, post("/api/v1/admin/enrollments"), Map.of("plotId", pending, "seasonId", seasonId, "status", "COVERED"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLOT_NOT_ACTIVE"));
        String body = as(admin, post("/api/v1/admin/enrollments"),
                Map.of("plotId", plotId, "seasonId", seasonId, "status", "COVERED"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sumInsured").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        long id = mapper.readTree(body).get("id").asLong();
        as(admin, post("/api/v1/admin/enrollments"), Map.of("plotId", plotId, "seasonId", seasonId, "status", "COVERED"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ENROLLMENT_EXISTS"));
        as(admin, put("/api/v1/admin/enrollments/" + id), Map.of("plotId", plotId, "seasonId", seasonId,
                "status", "COVERED", "sumInsured", 150000, "premium", 4500))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sumInsured").value(150000.0));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action = 'ENROLLMENT_CHANGED'", Long.class))
                .isEqualTo(1);
    }

    @Test
    void theInsurersRateMakesTheApprovedPayout() throws Exception {
        jdbc.update("INSERT INTO enrollments (plot_id, season_id, status, sum_insured) VALUES (?, ?, 'COVERED', 200000)",
                plotId, seasonId);
        long id = claim(plotId, seasonId, "UNDER_REVIEW", "{}");
        jdbc.update("INSERT INTO payout_accounts (user_id, provider, msisdn, verified) "
                + "SELECT farmer_id, 'MTN_MOMO', '+250782222222', TRUE FROM plots WHERE id = ?", plotId);

        as(admin, put("/api/v1/admin/claims/" + id + "/payout-rate"), Map.of("payoutRate", 1.5))
                .andExpect(status().isBadRequest());
        as(admin, put("/api/v1/admin/claims/" + id + "/payout-rate"), Map.of("payoutRate", 0.25))
                .andExpect(status().isOk()).andExpect(jsonPath("$.payoutRate").value(0.25));
        as(officer, post("/api/v1/claims/" + id + "/decision"), Map.of("decision", "APPROVED", "reason", "Confirmed loss."))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.amount").value(50000.0));
        as(admin, put("/api/v1/admin/claims/" + id + "/payout-rate"), Map.of("payoutRate", 0.5))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLAIM_CLOSED_FOR_RATE"));
    }

    // ===== Users and cooperatives =====

    @Test
    void adminCreatesAnAgentWhoCanLogIn() throws Exception {
        as(admin, post("/api/v1/admin/users"), Map.of("phone", "0781111104", "fullName", "New Agent", "role", "AGENT"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COOPERATIVE_REQUIRED"));
        as(admin, post("/api/v1/admin/users"), Map.of("phone", "0781111104", "fullName", "New Agent", "role", "AGENT",
                "cooperativeId", cooperativeId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phone").value("+250781111104"))
                .andExpect(jsonPath("$.cooperativeName").value("Test Cooperative"));
        as(admin, post("/api/v1/admin/users"), Map.of("phone", "0781111104", "fullName", "Again", "role", "OFFICER"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PHONE_IN_USE"));
        as(admin, post("/api/v1/admin/users"), Map.of("phone", "0781111105", "fullName", "Farmer", "role", "FARMER"))
                .andExpect(status().isBadRequest());

        requestOtp("+250781111104");
        postJson("/api/v1/auth/otp/verify", Map.of("phone", "+250781111104", "code", lastCode("+250781111104")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.role").value("AGENT"));
    }

    @Test
    void aSuspendedAccountCannotLogIn() throws Exception {
        long farmerId = jdbc.queryForObject("SELECT id FROM users WHERE phone = '+250781111101'", Long.class);
        as(admin, put("/api/v1/admin/users/" + farmerId + "/status"), Map.of("status", "SUSPENDED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUSPENDED"));
        jdbc.update("DELETE FROM otp_codes WHERE phone = '+250781111101'"); // skip the 60 s resend wait
        requestOtp("+250781111101");
        postJson("/api/v1/auth/otp/verify", Map.of("phone", "+250781111101", "code", lastCode("+250781111101")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));

        long adminId = jdbc.queryForObject("SELECT id FROM users WHERE phone = '+250781111103'", Long.class);
        as(admin, put("/api/v1/admin/users/" + adminId + "/status"), Map.of("status", "SUSPENDED"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CANNOT_CHANGE_SELF"));
    }

    @Test
    void adminAddsACooperative() throws Exception {
        as(admin, post("/api/v1/admin/cooperatives"), Map.of("name", "Kayonza Maize Growers", "district", "Kayonza"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.district").value("Kayonza"));
        mvc.perform(get("/api/v1/auth/cooperatives"))
                .andExpect(jsonPath("$[?(@.name == 'Kayonza Maize Growers')]").exists());
    }
}
