package com.rinda.backend.payout;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;

public final class PayoutDtos {

    private PayoutDtos() {
    }

    public record PayoutAccountRequest(
            @NotNull @Pattern(regexp = "MTN_MOMO|AIRTEL") String provider,
            @NotBlank @Schema(example = "0781234567") String msisdn) {
    }

    public record PayoutVerifyRequest(@NotBlank @Pattern(regexp = "\\d{6}") String code) {
    }

    public record PayoutAccountResponse(
            @Schema(allowableValues = {"MTN_MOMO", "AIRTEL"}) String provider,
            String msisdn,
            @Schema(description = "True once the farmer confirmed the number with the code sent to it") boolean verified,
            @Schema(description = "Whether the account name matches the farmer's name") boolean nameMatch,
            @Schema(allowableValues = {"SIMULATED"},
                    description = "The name check is simulated: Rinda has no access to mobile-money accounts")
            String nameCheck,
            @Schema(types = {"string", "null"}) Instant verifiedAt) {
    }
}
