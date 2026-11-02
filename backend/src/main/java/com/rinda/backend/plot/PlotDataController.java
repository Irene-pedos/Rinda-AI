package com.rinda.backend.plot;

import com.rinda.backend.auth.AuthPrincipal;
import com.rinda.backend.plot.PlotDataDtos.BaselineResponse;
import com.rinda.backend.plot.PlotDataDtos.ObservationResponse;
import com.rinda.backend.plot.PlotDataDtos.WeatherResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/plots/{id}")
@Tag(name = "Plot data")
public class PlotDataController {

    private final PlotDataService service;

    public PlotDataController(PlotDataService service) {
        this.service = service;
    }

    @Operation(summary = "NDVI readings for the crop health chart, oldest first")
    @GetMapping("/observations")
    public List<ObservationResponse> observations(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.observations(principal, id, from, to);
    }

    @Operation(summary = "Daily rain for the rain chart, oldest first")
    @GetMapping("/weather")
    public List<WeatherResponse> weather(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.weather(principal, id, from, to);
    }

    @Operation(summary = "The normal band for the crop health chart, by days after planting")
    @GetMapping("/baseline")
    public BaselineResponse baseline(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id) {
        return service.baseline(principal, id);
    }
}
