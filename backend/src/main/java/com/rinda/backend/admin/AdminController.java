package com.rinda.backend.admin;

import com.rinda.backend.admin.AdminDtos.AuditLogEntry;
import com.rinda.backend.admin.AdminDtos.CooperativeRequest;
import com.rinda.backend.admin.AdminDtos.EnrollmentRequest;
import com.rinda.backend.admin.AdminDtos.EnrollmentResponse;
import com.rinda.backend.admin.AdminDtos.PayoutRateRequest;
import com.rinda.backend.admin.AdminDtos.SeasonRequest;
import com.rinda.backend.admin.AdminDtos.SeasonResponse;
import com.rinda.backend.admin.AdminDtos.ThresholdResponse;
import com.rinda.backend.admin.AdminDtos.ThresholdUpdate;
import com.rinda.backend.admin.AdminDtos.UserRequest;
import com.rinda.backend.admin.AdminDtos.UserResponse;
import com.rinda.backend.admin.AdminDtos.UserStatusRequest;
import com.rinda.backend.auth.AuthPrincipal;
import com.rinda.backend.claim.ClaimDtos.ClaimResponse;
import com.rinda.backend.claim.ClaimService;
import com.rinda.backend.common.PageResponse;
import com.rinda.backend.user.CooperativeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Admin settings (spec §1: manage cooperatives, approve agents, set seasons and thresholds). All changes are audited. */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminConfigService config;
    private final AdminUserService users;
    private final ClaimService claims;

    public AdminController(AdminConfigService config, AdminUserService users, ClaimService claims) {
        this.config = config;
        this.users = users;
        this.claims = claims;
    }

    // ===== Thresholds =====

    @Operation(summary = "Every decision value, with its status (TO_CONFIRM, CONFIRMED_FOR_DEMO, CONFIRMED)")
    @GetMapping("/thresholds")
    public List<ThresholdResponse> thresholds() {
        return config.thresholds();
    }

    @Operation(summary = "Set a decision value from the team or insurer. null = not supplied yet.")
    @PutMapping("/thresholds/{key}")
    public ThresholdResponse updateThreshold(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String key,
                                             @Valid @RequestBody ThresholdUpdate request) {
        return config.updateThreshold(principal.userId(), key, request.value(), request.status());
    }

    // ===== Seasons =====

    @Operation(summary = "Seasons")
    @GetMapping("/seasons")
    public List<SeasonResponse> seasons() {
        return config.seasons();
    }

    @Operation(summary = "Add a season")
    @PostMapping("/seasons")
    @ResponseStatus(HttpStatus.CREATED)
    public SeasonResponse createSeason(@AuthenticationPrincipal AuthPrincipal principal,
                                       @Valid @RequestBody SeasonRequest request) {
        return config.createSeason(principal.userId(), request);
    }

    @Operation(summary = "Change a season, for example to confirm its dates")
    @PutMapping("/seasons/{id}")
    public SeasonResponse updateSeason(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id,
                                       @Valid @RequestBody SeasonRequest request) {
        return config.updateSeason(principal.userId(), id, request);
    }

    // ===== Insurance cover =====

    @Operation(summary = "Covers (enrollments)")
    @GetMapping("/enrollments")
    public PageResponse<EnrollmentResponse> enrollments(@RequestParam(required = false) Long seasonId,
                                                        @RequestParam(required = false) Long plotId,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        return config.enrollments(seasonId, plotId, page, size);
    }

    @Operation(summary = "Cover a plot for a season. Sum insured and premium come from the insurer.")
    @PostMapping("/enrollments")
    @ResponseStatus(HttpStatus.CREATED)
    public EnrollmentResponse createEnrollment(@AuthenticationPrincipal AuthPrincipal principal,
                                               @Valid @RequestBody EnrollmentRequest request) {
        return config.createEnrollment(principal.userId(), request);
    }

    @Operation(summary = "Change a cover: status, dates, sum insured, premium")
    @PutMapping("/enrollments/{id}")
    public EnrollmentResponse updateEnrollment(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id,
                                               @Valid @RequestBody EnrollmentRequest request) {
        return config.updateEnrollment(principal.userId(), id, request);
    }

    // ===== Claim payout rate =====

    @Operation(summary = "Enter the insurer's payout rate for a claim (0 to 1 of the sum insured)")
    @PutMapping("/claims/{id}/payout-rate")
    public ClaimResponse setPayoutRate(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id,
                                       @Valid @RequestBody PayoutRateRequest request) {
        config.setPayoutRate(principal.userId(), id, request.payoutRate());
        return claims.get(principal, id);
    }

    // ===== Audit log =====

    @Operation(summary = "Audit log, newest first")
    @GetMapping("/audit-log")
    public PageResponse<AuditLogEntry> auditLog(@RequestParam(required = false) String entity,
                                                @RequestParam(required = false) Long entityId,
                                                @RequestParam(required = false) String action,
                                                @RequestParam(required = false) Long actorId,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "50") int size) {
        return config.auditLog(entity, entityId, action, actorId, page, size);
    }

    // ===== Users and cooperatives =====

    @Operation(summary = "Accounts")
    @GetMapping("/users")
    public PageResponse<UserResponse> users(@RequestParam(required = false) String role,
                                            @RequestParam(required = false) String q,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return users.list(role, q, page, size);
    }

    @Operation(summary = "Create an agent, officer or admin. Farmers sign up in the app.")
    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(@AuthenticationPrincipal AuthPrincipal principal,
                                   @Valid @RequestBody UserRequest request) {
        return users.create(principal.userId(), request);
    }

    @Operation(summary = "Suspend or reactivate an account")
    @PutMapping("/users/{id}/status")
    public UserResponse setUserStatus(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id,
                                      @Valid @RequestBody UserStatusRequest request) {
        return users.setStatus(principal.userId(), id, request.status());
    }

    @Operation(summary = "Add a cooperative")
    @PostMapping("/cooperatives")
    @ResponseStatus(HttpStatus.CREATED)
    public CooperativeResponse createCooperative(@AuthenticationPrincipal AuthPrincipal principal,
                                                 @Valid @RequestBody CooperativeRequest request) {
        return users.createCooperative(principal.userId(), request);
    }
}
