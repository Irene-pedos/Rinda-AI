package com.rinda.backend.notification;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public final class NotificationDtos {

    private NotificationDtos() {
    }

    public record NotificationResponse(
            long id,
            @Schema(allowableValues = {"HEALTH", "CLAIM", "SYSTEM"},
                    description = "HEALTH opens plot detail, CLAIM opens claim detail") String type,
            @Schema(description = "Stable value to translate in the app, such as HEALTH_AT_RISK") String code,
            @Schema(description = "English text. The app should show its own text for the code.") String title,
            String body,
            @Schema(types = {"integer", "null"}) Long plotId,
            @Schema(types = {"string", "null"}) String plotName,
            @Schema(types = {"string", "null"}) String link,
            boolean isRead,
            @Schema(description = "When true, show a Demo label") boolean isDemo,
            Instant createdAt) {
    }
}
