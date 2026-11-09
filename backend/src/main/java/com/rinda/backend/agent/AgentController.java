package com.rinda.backend.agent;

import com.rinda.backend.agent.AgentDtos.FarmerSummaryResponse;
import com.rinda.backend.agent.AgentDtos.FieldVisitResponse;
import com.rinda.backend.agent.AgentDtos.PlotVerifyRequest;
import com.rinda.backend.auth.AuthPrincipal;
import com.rinda.backend.plot.PlotDtos.PlotResponse;
import com.rinda.backend.plot.PlotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Cooperative agent screens (spec §9.8). Roles come from the account: farmers never reach these. */
@RestController
@RequestMapping("/api/v1/agent")
@Tag(name = "Agent")
@PreAuthorize("hasRole('AGENT')")
public class AgentController {

    private final AgentService service;
    private final PlotService plots;

    public AgentController(AgentService service, PlotService plots) {
        this.service = service;
        this.plots = plots;
    }

    @Operation(summary = "Farmers in your cooperative, those needing attention first")
    @GetMapping("/farmers")
    public List<FarmerSummaryResponse> farmers(@AuthenticationPrincipal AuthPrincipal principal) {
        return service.farmers(principal.userId());
    }

    @Operation(summary = "Verify or reject a new plot, optionally correcting its planting date")
    @PostMapping("/plots/{id}/verify")
    public PlotResponse verifyPlot(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id,
                                   @Valid @RequestBody PlotVerifyRequest request) {
        return plots.verify(principal, id, request.decision(), request.confirmedPlantingDate(),
                request.rejectionReason());
    }

    @Operation(summary = "Record a field visit (ground truth). Safe to resend with the same clientId.")
    @PostMapping(value = "/field-visits", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FieldVisitResponse> recordVisit(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam long plotId,
            @RequestParam String plantCondition,
            @RequestParam String observedCause,
            @RequestParam String growthStage,
            @RequestParam(required = false) Boolean moistureChecked,
            @RequestParam(required = false) Boolean standDensityChecked,
            @RequestParam(required = false) Boolean farmerPresent,
            @RequestParam(required = false) String notes,
            @RequestParam(required = false) BigDecimal lat,
            @RequestParam(required = false) BigDecimal lng,
            @RequestParam Instant visitedAt,
            @RequestParam(required = false) String clientId,
            @RequestPart(required = false) MultipartFile photo) {
        var created = service.recordVisit(principal.userId(), new AgentService.VisitInput(plotId, plantCondition,
                observedCause, growthStage, moistureChecked, standDensityChecked, farmerPresent, notes, lat, lng,
                visitedAt, clientId == null || clientId.isBlank() ? null : clientId.trim()), photo);
        return ResponseEntity.status(created.isNew() ? HttpStatus.CREATED : HttpStatus.OK).body(created.visit());
    }
}
