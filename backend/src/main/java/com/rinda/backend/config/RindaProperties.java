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
public record RindaProperties(@Valid Jwt jwt, @Valid Otp otp, @Valid Ml ml, @Valid Uploads uploads,
                              @Valid Analysis analysis) {

    /**
     * "Run analysis". runInline processes a run in the calling thread (tests only); otherwise it runs in the
     * background. maxAttempts covers ML-service and network failures; a refusal (4xx) is never retried.
     */
    public record Analysis(boolean runInline, @Min(1) int maxAttempts) {
    }

    /** Where evidence photos are kept. Never a public web folder: photos are served only after a visibility check. */
    public record Uploads(@NotNull String dir) {
    }

    /** baseUrl is the ML service the backend calls for "Run analysis"; timeout allows for slow Earth Engine calls. */
    public record Ml(
            @NotNull @Size(min = 32, message = "ML_INTERNAL_API_KEY must be at least 32 characters") String internalApiKey,
            @NotNull String baseUrl,
            @NotNull Duration timeout) {
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
