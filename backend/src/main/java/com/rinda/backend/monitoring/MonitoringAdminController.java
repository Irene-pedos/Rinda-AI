package com.rinda.backend.monitoring;

import com.rinda.backend.monitoring.MonitoringDtos.RefreshResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/monitoring")
@Tag(name = "Admin")
@PreAuthorize("hasRole('ADMIN')")
public class MonitoringAdminController {

    private final MonitoringService service;

    public MonitoringAdminController(MonitoringService service) {
        this.service = service;
    }

    @Operation(summary = "Run the weekly satellite refresh now, for every walked plot that needs a fetch")
    @PostMapping("/refresh")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RefreshResponse refresh() {
        return new RefreshResponse(service.refreshAll("MANUAL"));
    }
}
