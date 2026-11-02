package com.rinda.backend.plot;

import com.rinda.backend.auth.AuthPrincipal;
import com.rinda.backend.common.PageResponse;
import com.rinda.backend.plot.PlotDtos.PlotRequest;
import com.rinda.backend.plot.PlotDtos.PlotResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/plots")
@Tag(name = "Plots")
public class PlotController {

    private final PlotService service;

    public PlotController(PlotService service) {
        this.service = service;
    }

    @Operation(summary = "List plots: farmers see their own, agents their cooperative's, officers and admins all")
    @GetMapping
    public PageResponse<PlotResponse> list(@AuthenticationPrincipal AuthPrincipal principal,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return service.list(principal, page, size);
    }

    @Operation(summary = "Get one plot. Returns 404 if the plot is not visible to this user.")
    @GetMapping("/{id}")
    public PlotResponse get(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id) {
        return service.get(principal, id);
    }

    @Operation(summary = "Register a maize plot. It starts as PENDING_VERIFICATION.")
    @PostMapping
    @PreAuthorize("hasRole('FARMER')")
    @ResponseStatus(HttpStatus.CREATED)
    public PlotResponse create(@AuthenticationPrincipal AuthPrincipal principal,
                               @Valid @RequestBody PlotRequest request) {
        return service.create(principal, request);
    }

    @Operation(summary = "Edit your plot. After verification, only the name can change (409 PLOT_LOCKED).")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('FARMER')")
    public PlotResponse update(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id,
                               @Valid @RequestBody PlotRequest request) {
        return service.update(principal, id, request);
    }

    @Operation(summary = "Delete your plot. Returns 409 PLOT_DELETE_DEFERRED if it has active cover or an open claim.")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('FARMER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id) {
        service.delete(principal, id);
    }
}
