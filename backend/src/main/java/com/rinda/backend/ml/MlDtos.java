package com.rinda.backend.ml;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class MlDtos {

    private MlDtos() {
    }

    public enum DataOrigin {
        GEE, SAMPLE_FILE
    }

    public record MlResultRequest(
            @NotNull Long plotId,
            @NotNull DataOrigin dataOrigin,
            @NotBlank @Size(max = 30) String modelVersion,
            @NotNull LocalDate plantingDate,
            @NotNull LocalDate inputFrom,
            @NotNull LocalDate inputTo,
            @NotNull JsonNode params,
            @NotNull @Size(max = 2000) List<@Valid ObservationRow> observations,
            @NotNull @Size(max = 2000) List<@Valid WeatherRow> weather,
            @Valid BaselineResult baseline,
            @Valid StatusResult status) {
    }

    /** The cause list is closed on purpose: a satellite can't identify a pest or disease. */
    public record StatusResult(
            @NotNull LocalDate statusDate,
            @NotNull @Pattern(regexp = "HEALTHY|WATCH|AT_RISK|NOT_ENOUGH_DATA") String status,
            BigDecimal anomaly,
            @DecimalMin("0") @DecimalMax("9999") BigDecimal rainPct,
            @Pattern(regexp = "WATER_DEFICIT|UNEXPLAINED") String cause,
            @NotNull @Pattern(regexp = "HIGH|MEDIUM|LOW") String confidence,
            @NotNull JsonNode explanation) {
    }

    public record ObservationRow(
            @NotNull LocalDate date,
            @NotNull @Pattern(regexp = "S2|S1|S1_ESTIMATE") String source,
            @DecimalMin("-1") @DecimalMax("1") BigDecimal ndvi,
            @DecimalMin("-1") @DecimalMax("1") BigDecimal smoothedNdvi,
            @Min(0) Integer clearPixels,
            @DecimalMin("0") @DecimalMax("100") BigDecimal cloudPct) {
    }

    public record WeatherRow(@NotNull LocalDate date, @DecimalMin("0") BigDecimal rainMm) {
    }

    public record BaselineResult(
            @NotNull @Pattern(regexp = "A|B") String seasonType,
            @NotBlank @Size(max = 30) String version,
            @NotNull @Size(max = 100) List<@Valid BaselineBin> bins) {
    }

    public record BaselineBin(
            @NotNull @Min(0) Integer dapBin,
            @NotNull @DecimalMin("-1") @DecimalMax("1") BigDecimal p10,
            @NotNull @DecimalMin("-1") @DecimalMax("1") BigDecimal p50,
            @NotNull @DecimalMin("-1") @DecimalMax("1") BigDecimal p90) {
    }

    public record MlResultResponse(long runId, int observations, int weatherDays, int baselineBins) {
    }
}
