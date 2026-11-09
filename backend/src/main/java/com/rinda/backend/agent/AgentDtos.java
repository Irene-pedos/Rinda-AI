package com.rinda.backend.agent;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class AgentDtos {

    private AgentDtos() {
    }

    public record FarmerSummaryResponse(
            long farmerId,
            String fullName,
            String phone,
            @Schema(description = "Plots that are not archived") int plotsCount,
            @Schema(description = "Plots waiting for this agent's check") int pendingVerificationCount,
            @Schema(description = "Plots whose newest crop health status is AT_RISK") int atRiskCount,
            @Schema(description = "Claims not yet paid or final") int openClaimsCount,
            @Schema(description = "When true, show a Demo label") boolean isDemo) {
    }

    public record PlotVerifyRequest(
            @NotNull @Pattern(regexp = "ACTIVE|REJECTED") String decision,
            @Schema(types = {"string", "null"},
                    description = "The planting date the agent confirmed in the field. It can correct the farmer's date.")
            LocalDate confirmedPlantingDate,
            @Size(max = 500) @Schema(types = {"string", "null"}, description = "Required when rejecting")
            String rejectionReason) {
    }

    public record FieldVisitResponse(
            long id,
            long plotId,
            String plotName,
            long agentId,
            @Schema(allowableValues = {"GOOD", "STRESSED", "SEVERE_LOSS", "FAILED"}) String plantCondition,
            @Schema(allowableValues = {"WATER_DEFICIT", "EXCESS_WATER", "NUTRIENT", "OTHER"}) String observedCause,
            @Schema(allowableValues = {"EMERGENCE", "VEGETATIVE", "FLOWERING", "GRAIN", "MATURITY"}) String growthStage,
            @Schema(description = "The checklist as the agent ticked it") JsonNode checklist,
            @Schema(types = {"string", "null"}) String notes,
            @Schema(types = {"number", "null"}) BigDecimal lat,
            @Schema(types = {"number", "null"}) BigDecimal lng,
            Instant visitedAt,
            @Schema(types = {"integer", "null"}, description = "The plot's open claim this visit is evidence for")
            Long claimId,
            @Schema(types = {"string", "null"}) String photoUrl,
            Instant createdAt,
            boolean isDemo) {
    }
}
