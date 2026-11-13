package com.rinda.backend.monitoring;

import com.fasterxml.jackson.databind.JsonNode;
import com.rinda.backend.plot.VerificationLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class MonitoringDtos {

    private MonitoringDtos() {
    }

    /** The screen state (docs/02-satellite-monitoring.md, "UI states"). Never "live". */
    public enum MonitoringState {
        /** No walked boundary yet: "Walk the edge of your plot to start satellite monitoring." */
        NO_BOUNDARY,
        /** The first fetch is queued or running: "Fetching satellite data". */
        FETCHING,
        /** The first fetch failed; the next weekly refresh tries again. */
        FETCH_FAILED,
        /** Fetched, but no image had enough clear pixels: "No clear image since [date]; showing rainfall only". */
        NO_CLEAR_IMAGE,
        /** NDVI curve, rainfall, latest image date and the season flag. */
        DATA_AVAILABLE
    }

    /** The short version for plot lists and the insurer dashboard. */
    public record MonitoringSummary(MonitoringState state,
                                    @Schema(types = {"string", "null"}) LocalDate latestImageDate,
                                    @Schema(types = {"string", "null"},
                                            allowableValues = {"CONSISTENT", "INCONSISTENT", "NOT_ENOUGH_DATA"})
                                    String seasonFlag) {
    }

    public record ObservationView(LocalDate date, String source,
                                  @Schema(types = {"number", "null"}, description = "Median NDVI of the clear pixels")
                                  BigDecimal ndviMedian,
                                  int pixelCount,
                                  @Schema(types = {"number", "null"},
                                          description = "Share of the plot's pixels masked as cloud, shadow or not clear")
                                  BigDecimal cloudFraction,
                                  @Schema(description = "False when the image has fewer clear pixels than min_clear_pixels")
                                  boolean usable) {
    }

    public record RainView(LocalDate date, BigDecimal rainMm, String source) {
    }

    public record SeasonCheckView(
            @Schema(allowableValues = {"CONSISTENT", "INCONSISTENT", "NOT_ENOUGH_DATA"}) String flag,
            String reason, @Schema(allowableValues = {"MEDIUM", "LOW"}) String confidence, LocalDate checkDate,
            LocalDate plantingDate, JsonNode signals, String dataQualityNote, int imagesUsed,
            @Schema(types = {"integer", "null"}) Integer pixelsUsed,
            @Schema(types = {"string", "null"}) LocalDate latestImageDate, String modelVersion, String dataOrigin) {
    }

    public record JobView(long id, @Schema(allowableValues = {"BACKFILL", "REFRESH"}) String kind,
                          @Schema(allowableValues = {"QUEUED", "RUNNING", "DONE", "FAILED", "INTERRUPTED"}) String status,
                          @Schema(types = {"string", "null"}) LocalDate fetchFrom,
                          @Schema(types = {"string", "null"}) LocalDate fetchTo,
                          @Schema(types = {"string", "null"}) String errorCode,
                          Instant createdAt, @Schema(types = {"string", "null"}) Instant finishedAt) {
    }

    public record MonitoringResponse(
            long plotId,
            MonitoringState state,
            @Schema(description = "Plain-words text for the state, in English. Apps may use their own translated copy.")
            String message,
            VerificationLevel verificationLevel,
            @Schema(description = "Null at FIELD_VERIFIED or higher; otherwise says the plot is not verified yet")
            String verificationNote,
            @Schema(description = "Crop health and the claim trigger run only on FIELD_VERIFIED plots")
            boolean healthStatusAvailable,
            boolean isDemo,
            @Schema(types = {"string", "null"}, allowableValues = {"GEE", "SAMPLE_FILE"},
                    description = "SAMPLE_FILE is simulated data, on demo plots only")
            String dataOrigin,
            @Schema(types = {"string", "null"}, description = "Date of the latest usable image. Always show it.")
            LocalDate latestImageDate,
            @Schema(types = {"string", "null"}, description = "Start of the fetched period (the planting date)")
            LocalDate monitoredSince,
            @Schema(types = {"string", "null"}) Instant lastFetchedAt,
            @Schema(description = "A fetch is queued or running now") boolean refreshing,
            @Schema(allowableValues = "WEEKLY") String updateFrequency,
            @Schema(types = {"integer", "null"}, description = "Empty means no image counts as usable yet")
            Integer minClearPixels,
            @Schema(types = {"object", "null"}) SeasonCheckView seasonCheck,
            List<ObservationView> observations,
            List<RainView> rainfall,
            @Schema(types = {"object", "null"}) JobView latestJob,
            List<String> notes) {
    }

    public record RefreshResponse(int jobsQueued) {
    }
}
