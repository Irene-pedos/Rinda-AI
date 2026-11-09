package com.rinda.backend.analysis;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class AnalysisDtos {

    private AnalysisDtos() {
    }

    public record AnalysisRunRequest(
            @Pattern(regexp = "GEE|SAMPLE")
            @Schema(description = "GEE (real satellite data, the default) or SAMPLE (simulated, demo plots only)")
            String source,
            @Size(max = 500)
            @Schema(description = "Plots to analyse. For GEE, leave out to analyse every eligible real plot. "
                    + "Required for SAMPLE.") List<Long> plotIds,
            @PastOrPresent @Schema(description = "Last day of data to include. Defaults to today.") LocalDate asOf) {
    }

    public record AnalysisRunResponse(
            long id,
            @Schema(allowableValues = {"GEE", "SAMPLE"}) String source,
            @Schema(allowableValues = {"QUEUED", "RUNNING", "DONE", "FAILED", "INTERRUPTED"}) String status,
            @Schema(allowableValues = {"MANUAL", "SCHEDULE"}) String triggerType,
            @Schema(types = {"integer", "null"}) Long requestedBy,
            @Schema(types = {"string", "null"}) LocalDate asOf,
            int plotsTotal,
            int plotsDone,
            int plotsFailed,
            int plotsSkipped,
            Instant createdAt,
            @Schema(types = {"string", "null"}) Instant startedAt,
            @Schema(types = {"string", "null"}) Instant finishedAt,
            @Schema(description = "Per plot. Only in GET /admin/analysis-runs/{id}; null in the list.")
            List<AnalysisItemResponse> items) {
    }

    public record AnalysisItemResponse(
            long plotId,
            String plotName,
            @Schema(allowableValues = {"QUEUED", "RUNNING", "DONE", "FAILED", "SKIPPED"}) String status,
            int attempts,
            @Schema(types = {"string", "null"}, description = "Why it was skipped or failed") String errorCode,
            @Schema(types = {"string", "null"}) String error,
            @Schema(types = {"integer", "null"}, description = "The saved ML result (ml_runs id)") Long mlRunId,
            @Schema(types = {"string", "null"}) Instant finishedAt) {
    }
}
