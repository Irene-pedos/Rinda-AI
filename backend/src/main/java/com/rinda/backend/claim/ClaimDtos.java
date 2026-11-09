package com.rinda.backend.claim;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class ClaimDtos {

    private ClaimDtos() {
    }

    public record ClaimResponse(
            long id,
            long plotId,
            String plotName,
            long seasonId,
            @Schema(types = {"string", "null"}) String seasonCode,
            @Schema(description = "The date the trigger rule was met") LocalDate triggerDate,
            @Schema(allowableValues = {"UNDER_REVIEW", "FIELD_VISIT_REQUESTED", "APPROVED", "DECLINED", "PAID",
                    "REVIEW_REQUESTED", "FINAL"},
                    description = "Always take it from the server, never from local data") String status,
            @Schema(types = {"number", "null"},
                    description = "Null until the insurer confirms the rates. Show 'not confirmed', never 0.")
            BigDecimal payoutRate,
            @Schema(types = {"number", "null"},
                    description = "Null until the insurer confirms the rates. Show 'not confirmed', never 0.")
            BigDecimal amount,
            @Schema(description = "Frozen evidence from when the claim started. Only in GET /claims/{id}; "
                    + "null in the list.") JsonNode evidenceSnapshot,
            @Schema(description = "The latest officer decision, or null") DecisionResponse decision,
            String modelVersion,
            @Schema(description = "When true, show a Demo label") boolean isDemo,
            Instant createdAt,
            @Schema(description = "The simulated payout, or null") PayoutResponse payout,
            @Schema(description = "The farmer's latest review request, or null") ReviewResponse review,
            @Schema(description = "True when the farmer may ask for a review now (one per decision)")
            boolean canRequestReview) {
    }

    public record DecisionRequest(
            @NotNull @Pattern(regexp = "APPROVED|DECLINED") String decision,
            @NotBlank @Size(max = 2000) @Schema(description = "Required for every decision; the farmer reads it")
            String reason) {
    }

    public record ReviewRequest(
            @NotBlank @Size(max = 500) @Schema(description = "What the farmer sees in the field") String message) {
    }

    public record PayoutResponse(
            BigDecimal amount,
            String reference,
            @Schema(allowableValues = {"PENDING", "PAID", "FAILED"}) String status,
            @Schema(description = "Always true: Rinda never moves real money") boolean isSimulated,
            Instant createdAt) {
    }

    public record ReviewResponse(
            String message,
            @Schema(allowableValues = {"OPEN", "ANSWERED"}) String status,
            @Schema(types = {"string", "null"}) String answer,
            Instant createdAt) {
    }

    public record DecisionResponse(
            @Schema(allowableValues = {"APPROVED", "DECLINED"}) String decision,
            String reason,
            Instant decidedAt) {
    }
}
