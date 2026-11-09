package com.rinda.backend.claim;

import com.rinda.backend.auth.AuthPrincipal;
import com.rinda.backend.claim.ClaimDtos.ClaimResponse;
import com.rinda.backend.claim.ClaimDtos.DecisionRequest;
import com.rinda.backend.claim.ClaimDtos.ReviewRequest;
import com.rinda.backend.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * There is no create endpoint, and never will be: only the parametric trigger creates claims.
 * Every action returns the claim as it now is, so the app never sets a claim status itself.
 */
@RestController
@RequestMapping("/api/v1/claims")
@Tag(name = "Claims")
public class ClaimController {

    private final ClaimService service;
    private final ClaimActionService actions;

    public ClaimController(ClaimService service, ClaimActionService actions) {
        this.service = service;
        this.actions = actions;
    }

    @Operation(summary = "Claims you can see, newest first (without the evidence snapshot)")
    @GetMapping
    public PageResponse<ClaimResponse> list(@AuthenticationPrincipal AuthPrincipal principal,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return service.list(principal, page, size);
    }

    @Operation(summary = "One claim with its frozen evidence snapshot")
    @GetMapping("/{id}")
    public ClaimResponse get(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id) {
        return service.get(principal, id);
    }

    @Operation(summary = "Officer: approve or decline, always with a written reason")
    @PostMapping("/{id}/decision")
    @PreAuthorize("hasRole('OFFICER')")
    public ClaimResponse decide(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id,
                                @Valid @RequestBody DecisionRequest request) {
        actions.decide(principal.userId(), id, request.decision(), request.reason());
        return service.get(principal, id);
    }

    @Operation(summary = "Officer: ask the cooperative agent to visit the field before deciding")
    @PostMapping("/{id}/field-visit-request")
    @PreAuthorize("hasRole('OFFICER')")
    public ClaimResponse requestFieldVisit(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id) {
        actions.requestFieldVisit(principal.userId(), id);
        return service.get(principal, id);
    }

    @Operation(summary = "Officer: send the simulated payout of an approved claim")
    @PostMapping("/{id}/payout")
    @PreAuthorize("hasRole('OFFICER')")
    public ClaimResponse pay(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id) {
        actions.pay(principal.userId(), id);
        return service.get(principal, id);
    }

    @Operation(summary = "Farmer: ask for a review of the decision (once per decision)")
    @PostMapping("/{id}/review-request")
    @PreAuthorize("hasRole('FARMER')")
    public ClaimResponse requestReview(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id,
                                       @Valid @RequestBody ReviewRequest request) {
        actions.requestReview(principal.userId(), id, request.message());
        return service.get(principal, id);
    }
}
