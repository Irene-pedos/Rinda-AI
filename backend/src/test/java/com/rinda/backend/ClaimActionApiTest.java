package com.rinda.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Officer decisions, review requests and simulated payouts (spec §3.7, phase 2). */
class ClaimActionApiTest extends ApiTestBase {

    private static final List<List<Double>> SQUARE = List.of(List.of(30.100, -1.950), List.of(30.102, -1.950),
            List.of(30.102, -1.948), List.of(30.100, -1.948), List.of(30.100, -1.950));
    private static final String PAYOUT_NUMBER = "+250782222222";

    private String farmer;
    private String officer;
    private long plotId;
    private long seasonId;
    private long claimId;

    @BeforeEach
    void claimUnderReview() throws Exception {
        farmer = farmerToken("+250786666661");
        officer = staffToken("+250786666662", "OFFICER", null);
        plotId = pendingPlot(farmer, "Hill field", "2026-09-20");
        jdbc.update("UPDATE plots SET status = 'ACTIVE', verification_level = 'FIELD_VERIFIED' WHERE id = ?", plotId);
        jdbc.update("INSERT INTO seasons (code, type) VALUES ('2026A', 'A')");
        seasonId = jdbc.queryForObject("SELECT id FROM seasons WHERE code = '2026A'", Long.class);
        jdbc.update("INSERT INTO enrollments (plot_id, season_id, status) VALUES (?, ?, 'COVERED')", plotId, seasonId);
        // As the trigger would create it (tested in ClaimTriggerApiTest)
        jdbc.update("""
                INSERT INTO claims (plot_id, season_id, trigger_date, status, evidence_snapshot_json, model_version)
                VALUES (?, ?, '2026-11-29', 'UNDER_REVIEW', '{}', '0.3.0')
                """, plotId, seasonId);
        claimId = jdbc.queryForObject("SELECT id FROM claims", Long.class);
    }

    private ResultActions as(String token, String path, Object body) throws Exception {
        return mvc.perform(post("/api/v1/claims/" + claimId + path).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body == null ? "{}" : mapper.writeValueAsString(body)));
    }

    private ResultActions decide(String decision) throws Exception {
        return as(officer, "/decision", Map.of("decision", decision, "reason", "Checked the satellite and rain data."));
    }

    private ResultActions review() throws Exception {
        return as(farmer, "/review-request", Map.of("message", "The low part of my field flooded."));
    }

    private String claimStatus() {
        return jdbc.queryForObject("SELECT status FROM claims WHERE id = ?", String.class, claimId);
    }

    private long notifications(String code) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE code = ?", Long.class, code);
    }

    private void confirmRates() {
        jdbc.update("UPDATE enrollments SET sum_insured = 100000 WHERE plot_id = ?", plotId);
        jdbc.update("UPDATE claims SET payout_rate = 0.5 WHERE id = ?", claimId);
    }

    private void verifiedPayoutAccount() throws Exception {
        mvc.perform(post("/api/v1/payout-account").header("Authorization", "Bearer " + farmer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("provider", "MTN_MOMO", "msisdn", PAYOUT_NUMBER))))
                .andExpect(status().isAccepted());
        mvc.perform(post("/api/v1/payout-account/verify").header("Authorization", "Bearer " + farmer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("code", lastCode(PAYOUT_NUMBER)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true));
    }

    // ===== Decisions =====

    @Test
    void officerApprovesWithAReasonAndTheFarmerSeesIt() throws Exception {
        decide("APPROVED").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.decision.decision").value("APPROVED"));

        mvc.perform(get("/api/v1/claims/" + claimId).header("Authorization", "Bearer " + farmer))
                .andExpect(jsonPath("$.decision.reason").value("Checked the satellite and rain data."))
                .andExpect(jsonPath("$.amount").doesNotExist())   // no confirmed rates: nothing is guessed
                .andExpect(jsonPath("$.payout").doesNotExist())
                .andExpect(jsonPath("$.canRequestReview").value(true));
        assertThat(notifications("CLAIM_APPROVED")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action = 'CLAIM_DECIDED'", Long.class))
                .isEqualTo(1);
    }

    @Test
    void everyDecisionNeedsAWrittenReason() throws Exception {
        as(officer, "/decision", Map.of("decision", "DECLINED", "reason", "  "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertThat(claimStatus()).isEqualTo("UNDER_REVIEW");
    }

    @Test
    void onlyOfficersDecide() throws Exception {
        as(farmer, "/decision", Map.of("decision", "APPROVED", "reason", "Please pay me."))
                .andExpect(status().isForbidden());
        String admin = staffToken("+250786666663", "ADMIN", null);
        as(admin, "/decision", Map.of("decision", "APPROVED", "reason", "x"))
                .andExpect(status().isForbidden());
        assertThat(claimStatus()).isEqualTo("UNDER_REVIEW");
    }

    @Test
    void aDecidedClaimCannotBeDecidedAgainWithoutAReview() throws Exception {
        decide("DECLINED").andExpect(status().isOk());
        decide("APPROVED").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLAIM_NOT_DECIDABLE"));
    }

    // ===== Reviews =====

    @Test
    void declinedClaimCanBeReviewedOnceAndASecondDeclineIsFinal() throws Exception {
        decide("DECLINED").andExpect(jsonPath("$.status").value("DECLINED"));
        assertThat(notifications("CLAIM_DECLINED")).isEqualTo(1);

        review().andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVIEW_REQUESTED"))
                .andExpect(jsonPath("$.review.status").value("OPEN"))
                .andExpect(jsonPath("$.canRequestReview").value(false));
        review().andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLAIM_NOT_REVIEWABLE"));

        decide("DECLINED").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINAL"))
                .andExpect(jsonPath("$.review.status").value("ANSWERED"))
                .andExpect(jsonPath("$.review.answer").value("Checked the satellite and rain data."));
        assertThat(notifications("CLAIM_FINAL")).isEqualTo(1);
        review().andExpect(status().isConflict());
    }

    @Test
    void aReviewCanTurnADeclineIntoAnApproval() throws Exception {
        decide("DECLINED");
        review();
        decide("APPROVED").andExpect(jsonPath("$.status").value("APPROVED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM claim_decisions WHERE answers_review_id IS NOT NULL",
                Long.class)).isEqualTo(1);
    }

    @Test
    void noReviewBeforeTheOfficerHasDecided() throws Exception {
        review().andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLAIM_NOT_REVIEWABLE"));
    }

    @Test
    void anotherFarmerCannotAskForAReview() throws Exception {
        decide("DECLINED");
        String other = farmerToken("+250786666664");
        mvc.perform(post("/api/v1/claims/" + claimId + "/review-request").header("Authorization", "Bearer " + other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("message", "Not mine"))))
                .andExpect(status().isNotFound());
    }

    // ===== Field visit =====

    @Test
    void officerCanAskForAFieldVisitAndTheAgentIsTold() throws Exception {
        staffToken("+250786666665", "AGENT", cooperativeId);
        as(officer, "/field-visit-request", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FIELD_VISIT_REQUESTED"));
        assertThat(notifications("CLAIM_FIELD_VISIT")).isEqualTo(1);
        assertThat(notifications("FIELD_VISIT_NEEDED")).isEqualTo(1);
        decide("APPROVED").andExpect(status().isOk());
    }

    // ===== Simulated payouts =====

    @Test
    void noPayoutWithoutTheInsurersConfirmedAmount() throws Exception {
        verifiedPayoutAccount();
        decide("APPROVED").andExpect(jsonPath("$.status").value("APPROVED"));
        as(officer, "/payout", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYOUT_AMOUNT_NOT_CONFIRMED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payouts", Long.class)).isZero();
    }

    @Test
    void noPayoutWithoutAVerifiedAccountAndTheFarmerIsAskedToAddOne() throws Exception {
        confirmRates();
        decide("APPROVED").andExpect(jsonPath("$.status").value("APPROVED"));
        assertThat(notifications("PAYOUT_ACCOUNT_NEEDED")).isEqualTo(1);
        as(officer, "/payout", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYOUT_ACCOUNT_NOT_VERIFIED"));

        verifiedPayoutAccount();
        as(officer, "/payout", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.amount").value(50000.0))
                .andExpect(jsonPath("$.payout.isSimulated").value(true))
                .andExpect(jsonPath("$.payout.status").value("PAID"));
    }

    @Test
    void approvalPaysAtOnceWhenEverythingIsReady() throws Exception {
        confirmRates();
        verifiedPayoutAccount();
        decide("APPROVED").andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.canRequestReview").value(false));
        String reference = jdbc.queryForObject("SELECT reference FROM payouts", String.class);
        assertThat(reference).startsWith("SIM-" + claimId + "-");
        assertThat(jdbc.queryForObject("SELECT is_simulated FROM payouts", Boolean.class)).isTrue();
        assertThat(notifications("CLAIM_PAID")).isEqualTo(1);
        as(officer, "/payout", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLAIM_NOT_APPROVED"));
    }
}
