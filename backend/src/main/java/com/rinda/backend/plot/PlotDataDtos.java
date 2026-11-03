package com.rinda.backend.plot;

import com.fasterxml.jackson.databind.JsonNode;
import com.rinda.backend.ml.MlDtos.DataOrigin;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class PlotDataDtos {

    private PlotDataDtos() {
    }

    public record ObservationResponse(
            LocalDate date,
            @Schema(types = {"integer", "null"}, description = "Days after planting. Null if the planting date is unknown.")
            Integer dap,
            String source,
            @Schema(types = {"number", "null"}, description = "Null when too few clear pixels were available. Show a gap.")
            BigDecimal ndvi,
            @Schema(types = {"number", "null"}) BigDecimal smoothedNdvi,
            @Schema(types = {"integer", "null"}) Integer clearPixels,
            @Schema(types = {"number", "null"}) BigDecimal cloudPct,
            @Schema(description = "SAMPLE_FILE is simulated and must be labelled Demo") DataOrigin dataOrigin) {
    }

    public record WeatherResponse(
            LocalDate date,
            @Schema(types = {"integer", "null"}, description = "Days after planting. Null if the planting date is unknown.")
            Integer dap,
            @Schema(types = {"number", "null"}) BigDecimal rainMm,
            @Schema(description = "SAMPLE_FILE is simulated and must be labelled Demo") DataOrigin dataOrigin) {
    }

    /** Crop health, which is separate from a plot's verification status and from any claim. */
    public record StatusResponse(
            @Schema(types = {"string", "null"}, description = "Null if the plot has not been analysed yet") LocalDate date,
            @Schema(allowableValues = {"HEALTHY", "WATCH", "AT_RISK", "NOT_ENOUGH_DATA"}) String status,
            @Schema(types = {"number", "null"}) BigDecimal anomaly,
            @Schema(types = {"number", "null"}, description = "Rain over 30 days as a percent of normal. Null if unknown, never 0.")
            BigDecimal rainPct,
            @Schema(types = {"string", "null"}, allowableValues = {"WATER_DEFICIT", "UNEXPLAINED"}) String cause,
            @Schema(allowableValues = {"HIGH", "MEDIUM", "LOW"}) String confidence,
            boolean needsFieldVisit,
            @Schema(description = "What the status is based on. Each item has a code, and some have values.")
            JsonNode signals,
            String dataQualityNote,
            @Schema(types = {"string", "null"}) String modelVersion,
            @Schema(types = {"string", "null"}, description = "GEE, or SAMPLE_FILE for simulated data") DataOrigin dataOrigin,
            @Schema(types = {"string", "null"}, description = "Only with NOT_ENOUGH_DATA: the newest real status, which is not current")
            String lastKnownStatus,
            @Schema(types = {"string", "null"}, description = "The date of lastKnownStatus, for \"Last update: <date>\"")
            LocalDate lastKnownDate,
            @Schema(types = {"object", "null"}, description = "The threshold values this result used")
            JsonNode thresholdsUsed) {
    }

    /** A range against the plot's normal. When unavailable, reason says why and the range is null. */
    public record ForecastResponse(
            boolean available,
            @Schema(types = {"string", "null"}) LocalDate date,
            @Schema(types = {"number", "null"}, description = "Low end, as a percent of normal") BigDecimal lowPct,
            @Schema(types = {"number", "null"}, description = "High end, as a percent of normal") BigDecimal highPct,
            @Schema(allowableValues = {"HIGH", "MEDIUM", "LOW"}) String confidence,
            @Schema(types = {"string", "null"}, allowableValues = {"NOT_ANALYSED", "TOO_EARLY", "NOT_ENOUGH_DATA"})
            String reason,
            String dataQualityNote,
            @Schema(types = {"string", "null"}) String modelVersion,
            @Schema(types = {"string", "null"}, description = "GEE, or SAMPLE_FILE for simulated data") DataOrigin dataOrigin,
            @Schema(types = {"object", "null"}, description = "The threshold values this result used")
            JsonNode thresholdsUsed) {
    }

    /** The newest enrollment. Money stays null until the insurer confirms it, which isn't the same as 0. */
    public record EnrollmentResponse(
            @Schema(allowableValues = {"NOT_ENROLLED", "PENDING", "COVERED", "CANCELLED"}) String status,
            @Schema(types = {"string", "null"}) String seasonCode,
            @Schema(types = {"string", "null"}) LocalDate startDate,
            @Schema(types = {"string", "null"}) LocalDate endDate,
            @Schema(types = {"number", "null"}, description = "Null until the insurer confirms it") BigDecimal sumInsured,
            @Schema(types = {"number", "null"}, description = "Null until the insurer confirms it") BigDecimal premium,
            boolean isDemo) {
    }

    public record BaselineBinResponse(
            @Schema(description = "First day of the bin, in days after planting") int dapBin,
            BigDecimal p10, BigDecimal p50, BigDecimal p90,
            @Schema(description = "True if estimated from the bins on each side. Show it differently.") boolean filled) {
    }

    /** Before any analysis has run, bins is empty and the other fields are null. */
    public record BaselineResponse(
            @Schema(types = {"string", "null"}) String seasonType,
            @Schema(types = {"string", "null"}) String version,
            @Schema(types = {"integer", "null"}, description = "Days covered by each bin") Integer binDays,
            @Schema(types = {"string", "null"}, description = "GEE, or SAMPLE_FILE for simulated data") DataOrigin dataOrigin,
            List<BaselineBinResponse> bins) {
    }
}
