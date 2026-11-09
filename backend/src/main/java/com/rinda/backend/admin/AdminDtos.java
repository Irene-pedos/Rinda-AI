package com.rinda.backend.admin;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class AdminDtos {

    private AdminDtos() {
    }

    // ===== Thresholds =====

    public record ThresholdResponse(String key, @Schema(types = {"number", "null"}) BigDecimal value,
                                    @Schema(allowableValues = {"TO_CONFIRM", "CONFIRMED_FOR_DEMO", "CONFIRMED"})
                                    String status,
                                    String description,
                                    @Schema(types = {"string", "null"}) String updatedByName,
                                    Instant updatedAt) {
    }

    public record ThresholdUpdate(
            @Digits(integer = 7, fraction = 3)
            @Schema(types = {"number", "null"}, description = "null means 'not supplied yet', never zero")
            BigDecimal value,
            @NotNull @Pattern(regexp = "TO_CONFIRM|CONFIRMED_FOR_DEMO|CONFIRMED") String status) {
    }

    // ===== Seasons =====

    public record SeasonResponse(long id, String code, String type,
                                 @Schema(types = {"string", "null"}) LocalDate startDate,
                                 @Schema(types = {"string", "null"}) LocalDate endDate,
                                 String dataStatus, boolean isDemo) {
    }

    public record SeasonRequest(
            @NotBlank @Size(max = 10) @Schema(example = "2027A") String code,
            @NotNull @Pattern(regexp = "A|B") String type,
            LocalDate startDate,
            LocalDate endDate,
            @NotNull @Pattern(regexp = "TO_CONFIRM|CONFIRMED") String dataStatus) {
    }

    // ===== Enrollments (insurance cover) =====

    public record EnrollmentResponse(long id, long plotId, String plotName, String farmerName, long seasonId,
                                     String seasonCode, String status,
                                     @Schema(types = {"number", "null"}) BigDecimal sumInsured,
                                     @Schema(types = {"number", "null"}) BigDecimal premium,
                                     @Schema(types = {"string", "null"}) LocalDate startDate,
                                     @Schema(types = {"string", "null"}) LocalDate endDate,
                                     boolean isDemo) {
    }

    public record EnrollmentRequest(
            @NotNull Long plotId,
            @NotNull Long seasonId,
            @NotNull @Pattern(regexp = "PENDING|COVERED|CANCELLED") String status,
            @DecimalMin("0") @Digits(integer = 12, fraction = 2)
            @Schema(types = {"number", "null"}, description = "From the insurer. Leave null until confirmed.")
            BigDecimal sumInsured,
            @DecimalMin("0") @Digits(integer = 12, fraction = 2)
            @Schema(types = {"number", "null"}, description = "From the insurer. Leave null until confirmed.")
            BigDecimal premium,
            LocalDate startDate,
            LocalDate endDate) {
    }

    // ===== Claim payout rate =====

    public record PayoutRateRequest(
            @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("1") @Digits(integer = 1, fraction = 4)
            @Schema(description = "Share of the sum insured to pay, from the insurer's rate table (0 to 1)")
            BigDecimal payoutRate) {
    }

    // ===== Audit log =====

    public record AuditLogEntry(long id, String action, String entity, @Schema(types = {"integer", "null"}) Long entityId,
                                @Schema(types = {"integer", "null"}) Long actorId,
                                @Schema(types = {"string", "null"}) String actorName,
                                JsonNode before, JsonNode after, Instant occurredAt) {
    }

    // ===== Users and cooperatives =====

    public record UserResponse(long id, String phone, String fullName, String role, String status, String language,
                               @Schema(types = {"integer", "null"}) Long cooperativeId,
                               @Schema(types = {"string", "null"}) String cooperativeName,
                               boolean isDemo, Instant createdAt) {
    }

    public record UserRequest(
            @NotBlank @Schema(example = "0781234567") String phone,
            @NotBlank @Size(max = 120) String fullName,
            @NotNull @Pattern(regexp = "AGENT|OFFICER|ADMIN",
                    message = "Sign-up creates farmers. Admins create agents, officers and admins.") String role,
            @Schema(types = {"integer", "null"}, description = "Required for agents") Long cooperativeId,
            @Pattern(regexp = "rw|en") String language) {
    }

    public record UserStatusRequest(@NotNull @Pattern(regexp = "ACTIVE|SUSPENDED") String status) {
    }

    public record CooperativeRequest(@NotBlank @Size(max = 150) String name,
                                     @NotBlank @Size(max = 80) String district,
                                     @Size(max = 80) String sector) {
    }
}
