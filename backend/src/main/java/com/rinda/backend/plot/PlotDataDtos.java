package com.rinda.backend.plot;

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

    public record BaselineBinResponse(
            @Schema(description = "First day of the bin, in days after planting") int dapBin,
            BigDecimal p10, BigDecimal p50, BigDecimal p90) {
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
