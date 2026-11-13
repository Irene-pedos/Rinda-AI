package com.rinda.backend.plot;

import com.rinda.backend.monitoring.MonitoringDtos.MonitoringSummary;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class PlotDtos {

    private PlotDtos() {
    }

    @Schema(description = "GeoJSON Polygon with one closed ring of [longitude, latitude] points")
    public record GeoJsonPolygon(
            @NotBlank @Schema(example = "Polygon") String type,
            @NotNull List<List<List<Double>>> coordinates) {
    }

    /** Registration step 1. There is no boundary here: it comes only from POST /plots/{id}/walk. */
    public record PlotCreateRequest(
            @NotBlank @Size(max = 80) String name,
            @NotBlank @Size(max = 40) @Schema(description = "As typed from the farmer's own e-title",
                    example = "1/01/01/01/0001") String upi,
            @NotNull LocalDate plantingDate,
            @NotNull RelationshipToPlot relationshipToPlot,
            @NotNull @Schema(description = "Must be true: the farmer confirms they have the right to farm this "
                    + "plot and that a false claim can cancel cover") Boolean declarationAccepted,
            @Schema(description = "Optional. Only MAIZE is accepted.", example = "MAIZE") String crop,
            @Schema(hidden = true) Object boundary) {
    }

    public record PlotUpdateRequest(
            @NotBlank @Size(max = 80) String name,
            @NotNull LocalDate plantingDate,
            @Schema(hidden = true) Object boundary) {
    }

    @Schema(description = "One GPS fix recorded by the app while the farmer walks the plot's edge")
    public record WalkPoint(
            @NotNull Double lat,
            @NotNull Double lng,
            @NotNull @Positive @Schema(description = "The fix's horizontal accuracy in metres, from the phone")
            Double accuracyM,
            @NotNull Instant recordedAt,
            @Schema(description = "True when Android reports the fix came from a mock-location app")
            boolean mockLocation) {
    }

    public record WalkRequest(
            @NotNull @Size(min = 3, max = 499) @Schema(description = "In the order recorded. Don't repeat the first "
                    + "point at the end: the server closes the boundary.") List<@Valid @NotNull WalkPoint> points) {
    }

    public record FlagResolveRequest(@NotBlank @Size(max = 1000) String note) {
    }

    public record FlagResponse(long id, FlagType type, String details,
                               @Schema(types = {"integer", "null"}) Long relatedPlotId,
                               Instant createdAt,
                               @Schema(types = {"string", "null"}) Instant resolvedAt,
                               @Schema(types = {"integer", "null"}) Long resolvedBy,
                               @Schema(types = {"string", "null"}) String resolutionNote) {
    }

    public enum CheckResult { PASSED, FLAGGED, REVIEWED, PENDING, NOT_RUN }

    @Schema(description = "One registration check and its outcome. NOT_RUN is shown, never hidden.")
    public record CheckResponse(String check, CheckResult result, String note) {
    }

    public record DocumentResponse(long id, long plotId, DocumentType type, String contentType,
                                   @Schema(types = {"number", "null"},
                                           description = "Area typed by the farmer from the document, in m²")
                                   BigDecimal typedAreaM2,
                                   Instant uploadedAt, boolean isDemo) {
    }

    public record PlotResponse(long id, long farmerId,
                               @Schema(types = {"string", "null"}) String upi,
                               String name, String crop,
                               @Schema(types = {"object", "null"}, description = "Null until the plot is walked")
                               GeoJsonPolygon boundary,
                               @Schema(types = {"number", "null"}) BigDecimal areaHa,
                               @Schema(types = {"number", "null"}) BigDecimal walkedAreaM2,
                               @Schema(types = {"number", "null"},
                                       description = "Typed by the farmer from the document, not read from it")
                               BigDecimal documentAreaM2,
                               @Schema(types = {"number", "null"}) BigDecimal areaDiffPct,
                               @Schema(types = {"object", "null"},
                                       description = "Null means the whole walked boundary counts as planted")
                               GeoJsonPolygon plantedArea,
                               @Schema(types = {"string", "null"}, description = "Null only on seeded placeholder plots")
                               LocalDate plantingDate,
                               @Schema(types = {"string", "null"}) RelationshipToPlot relationshipToPlot,
                               PlotStatus status,
                               VerificationLevel verificationLevel,
                               @Schema(description = "True only at FIELD_VERIFIED or higher, with no open dispute")
                               boolean financialActionsAllowed,
                               List<FlagResponse> flags,
                               List<CheckResponse> checks,
                               @Schema(description = "Satellite state, latest image date and season flag. "
                                       + "Details: GET /plots/{id}/monitoring")
                               MonitoringSummary monitoring,
                               @Schema(allowableValues = {"CONFIRMED", "TO_CONFIRM"},
                                       description = "TO_CONFIRM marks a placeholder plot whose details are not real")
                               String dataStatus,
                               @Schema(description = "When true, every UI must show a Demo label") boolean isDemo,
                               Instant createdAt, Instant updatedAt) {
    }
}
