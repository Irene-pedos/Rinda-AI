package com.rinda.backend.report;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public final class ProblemReportDtos {

    private ProblemReportDtos() {
    }

    public record ProblemReportResponse(
            long id,
            long plotId,
            String plotName,
            long farmerId,
            @Schema(allowableValues = {"DRY_LEAVES", "YELLOW_LEAVES", "FLOODING", "OTHER"}) String reason,
            @Schema(types = {"string", "null"}) String note,
            boolean agentVisitRequested,
            @Schema(types = {"integer", "null"},
                    description = "The open claim this report was attached to as evidence. A report never creates one.")
            Long claimId,
            @Schema(types = {"string", "null"}, description = "Fetch with your token; null when there is no photo")
            String photoUrl,
            @Schema(types = {"string", "null"}, description = "When the farmer made the report on the phone")
            Instant reportedAt,
            Instant createdAt,
            @Schema(description = "When true, show a Demo label") boolean isDemo) {
    }
}
