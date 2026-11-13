package com.rinda.backend.plot.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rinda.backend.common.ApiException;
import com.rinda.backend.config.RindaProperties;
import com.rinda.backend.plot.PlotDtos.GeoJsonPolygon;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** "Type the UPI, see the parcel": the first step of registering a plot, before the GPS walk. */
@RestController
@RequestMapping("/api/v1/land-registry")
@Tag(name = "Plots")
public class LandRegistryController {

    /** The parcel as the provider knows it. A reference only: the plot's boundary comes from the GPS walk. */
    public record ParcelView(
            @Schema(description = "The parcel's shape as the provider has it. Never the plot's boundary.")
            GeoJsonPolygon boundary,
            BigDecimal areaM2, BigDecimal areaHa,
            @Schema(types = {"string", "null"}) String landUse,
            @Schema(types = {"string", "null"}) String location) {
    }

    public record ParcelLookupResponse(
            String upi,
            boolean found,
            @Schema(allowableValues = {"MOCK", "DOCUMENT"}) String source,
            @Schema(description = "True: invented demo data, not official land records. Always label it.")
            boolean isMock,
            @Schema(description = "Plain words to show with the result")
            String notice,
            @Schema(types = {"object", "null"}) ParcelView parcel) {
    }

    private final LandRegistryProvider registry;
    private final ObjectMapper mapper;
    private final Pattern upiPattern;

    public LandRegistryController(LandRegistryProvider registry, ObjectMapper mapper, RindaProperties properties) {
        this.registry = registry;
        this.mapper = mapper;
        this.upiPattern = Pattern.compile(properties.plots().upiPattern());
    }

    @Operation(summary = "Look up a UPI before registering: the parcel's shape, area, land use and location if the "
            + "registry provider has them. Never the owner's name. On the MOCK provider (dev only) the data is "
            + "invented, for fake UPIs only.")
    @GetMapping("/parcels")
    public ParcelLookupResponse lookup(@RequestParam String upi) {
        String trimmed = upi.trim();
        if (!upiPattern.matcher(trimmed).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UPI_INVALID",
                    "Type the UPI exactly as it is on your e-title, for example 1/01/01/01/0001.");
        }
        boolean mock = "MOCK".equals(registry.name());
        return registry.findByUpi(trimmed)
                .map(record -> new ParcelLookupResponse(trimmed, true, registry.name(), mock, mock
                        ? "Demo data, not official land records. Your plot's boundary still comes from walking its edge."
                        : "Parcel details from " + registry.name() + ". Your plot's boundary still comes from walking "
                                + "its edge.", toView(record)))
                .orElseGet(() -> new ParcelLookupResponse(trimmed, false, registry.name(), mock, mock
                        ? "No demo parcel has this UPI. You can still register: walk the edge of the plot and add "
                                + "your e-title."
                        : "Parcel details can't be looked up yet. Your e-title is the record: walk the edge of the "
                                + "plot and add your e-title.", null));
    }

    private ParcelView toView(PlotRecord record) {
        GeoJsonPolygon boundary;
        try {
            boundary = record.polygonGeojson() == null ? null
                    : mapper.readValue(record.polygonGeojson(), GeoJsonPolygon.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
        BigDecimal areaHa = record.areaM2() == null ? null
                : record.areaM2().divide(BigDecimal.valueOf(10_000), 4, RoundingMode.HALF_UP);
        return new ParcelView(boundary, record.areaM2(), areaHa, record.landUse(), record.locationAdmin());
    }
}
