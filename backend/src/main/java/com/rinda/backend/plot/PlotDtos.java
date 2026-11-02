package com.rinda.backend.plot;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

    public record PlotRequest(
            @NotBlank @Size(max = 80) String name,
            @NotNull @Valid GeoJsonPolygon boundary,
            @NotNull LocalDate plantingDate,
            @Schema(description = "Optional. Only MAIZE is accepted.", example = "MAIZE") String crop) {
    }

    public record PlotResponse(long id, long farmerId, String name, String crop, GeoJsonPolygon boundary,
                               BigDecimal areaHa,
                               @Schema(types = {"string", "null"}, description = "Null only on seeded placeholder plots")
                               LocalDate plantingDate,
                               PlotStatus status,
                               @Schema(allowableValues = {"CONFIRMED", "TO_CONFIRM"},
                                       description = "TO_CONFIRM marks a placeholder plot whose details are not real")
                               String dataStatus,
                               @Schema(description = "When true, every UI must show a Demo label") boolean isDemo,
                               Instant createdAt, Instant updatedAt) {
    }
}
