package com.rinda.backend.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("rinda")
public record RindaProperties(@Valid Jwt jwt, @Valid Otp otp, @Valid Ml ml) {

    public record Ml(
            @NotNull @Size(min = 32, message = "ML_INTERNAL_API_KEY must be at least 32 characters") String internalApiKey) {
    }

    public record Jwt(
            @NotNull @Size(min = 32, message = "JWT_SECRET must be at least 32 characters") String secret,
            @NotNull Duration accessTtl,
            @NotNull Duration refreshTtl) {
    }

    public record Otp(
            @NotNull Duration ttl,
            @Min(1) int maxAttempts,
            @NotNull Duration resendInterval,
            boolean logToConsole) {
    }
}
