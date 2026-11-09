package com.rinda.backend.officer;

import com.fasterxml.jackson.databind.JsonNode;
import com.rinda.backend.agent.AgentDtos.FieldVisitResponse;
import com.rinda.backend.claim.ClaimDtos.ClaimResponse;
import com.rinda.backend.report.ProblemReportDtos.ProblemReportResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class OfficerDtos {

    private OfficerDtos() {
    }

    /** One row of the officer's claims queue. */
    public record QueueItem(
            long id,
            String status,
            LocalDate triggerDate,
            @Schema(description = "Days since the claim started, while it is still open; null once closed")
            Integer daysOpen,
            long plotId,
            String plotName,
            BigDecimal areaHa,
            long farmerId,
            String farmerName,
            String farmerPhone,
            String cooperativeName,
            String seasonCode,
            @Schema(description = "What the officer should check, from the evidence snapshot") JsonNode reviewFlags,
            int problemReports,
            int fieldVisits,
            @Schema(description = "The farmer asked for a review that is not answered yet") boolean openReview,
            @Schema(types = {"number", "null"}) BigDecimal payoutRate,
            @Schema(types = {"number", "null"}) BigDecimal amount,
            String modelVersion,
            boolean isDemo) {
    }

    public record Farmer(long id, String fullName, String phone, String cooperativeName, boolean isDemo) {
    }

    public record Plot(long id, String name, BigDecimal areaHa, LocalDate plantingDate, String status,
                       String dataStatus, @Schema(types = {"string", "null"}) String verifiedByName, boolean isDemo) {
    }

    public record Cover(String status, String seasonCode, @Schema(types = {"number", "null"}) BigDecimal sumInsured,
                        @Schema(types = {"number", "null"}) BigDecimal premium,
                        @Schema(types = {"string", "null"}) LocalDate startDate,
                        @Schema(types = {"string", "null"}) LocalDate endDate) {
    }

    public record PayoutAccount(String provider,
                                @Schema(description = "Only the last 3 digits are shown") String msisdnMasked,
                                boolean verified) {
    }

    public record Decision(String decision, String reason, String officerName, Instant decidedAt,
                           @Schema(description = "True when this decision answered a review request")
                           boolean answeredReview) {
    }

    public record Review(String message, String status, @Schema(types = {"string", "null"}) String answer,
                         Instant createdAt, @Schema(types = {"string", "null"}) Instant answeredAt) {
    }

    public record AuditEntry(String action, @Schema(types = {"string", "null"}) String actorName,
                             @Schema(types = {"string", "null"}) String actorRole, JsonNode before, JsonNode after,
                             Instant occurredAt) {
    }

    /** Everything the officer needs to decide one claim, in one call. */
    public record CaseFile(
            ClaimResponse claim,
            Farmer farmer,
            Plot plot,
            @Schema(description = "The cover the claim was made under, or null") Cover cover,
            @Schema(description = "The farmer's payout account, or null when there is none") PayoutAccount payoutAccount,
            List<Decision> decisions,
            List<Review> reviews,
            @Schema(description = "Problem reports on the plot, newest first") List<ProblemReportResponse> problemReports,
            @Schema(description = "Field visits on the plot, newest first") List<FieldVisitResponse> fieldVisits,
            @Schema(description = "Everything that happened to this claim, oldest first") List<AuditEntry> history) {
    }

    public record Summary(
            @Schema(description = "Claims by status") Map<String, Long> claimsByStatus,
            @Schema(description = "Claims waiting for an officer: under review, field visit done or not, review requested")
            long awaitingDecision,
            long openReviews,
            @Schema(description = "Open claims with at least one review flag") long flaggedOpen,
            @Schema(description = "Approved but not paid: rates or the farmer's payout account are missing")
            long approvedUnpaid,
            @Schema(description = "Sum of simulated payouts") BigDecimal simulatedPaidTotal,
            @Schema(description = "Plots by verification status") Map<String, Long> plotsByStatus,
            @Schema(description = "Trigger values not supplied yet; while any is missing, no claim can start")
            List<String> triggerValuesMissing,
            @Schema(description = "The latest analysis run, or null") JsonNode latestAnalysisRun) {
    }
}
