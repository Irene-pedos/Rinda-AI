package com.rinda.backend.payout;

import com.rinda.backend.auth.AuthPrincipal;
import com.rinda.backend.payout.PayoutDtos.PayoutAccountRequest;
import com.rinda.backend.payout.PayoutDtos.PayoutAccountResponse;
import com.rinda.backend.payout.PayoutDtos.PayoutVerifyRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payout-account")
@Tag(name = "Payout account")
@PreAuthorize("hasRole('FARMER')")
public class PayoutAccountController {

    private final PayoutAccountService service;

    public PayoutAccountController(PayoutAccountService service) {
        this.service = service;
    }

    @Operation(summary = "Your mobile-money number for simulated payouts")
    @GetMapping
    public PayoutAccountResponse get(@AuthenticationPrincipal AuthPrincipal principal) {
        return service.get(principal.userId());
    }

    @Operation(summary = "Set or change the number. A code is sent to it unless it is already verified.")
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public PayoutAccountResponse set(@AuthenticationPrincipal AuthPrincipal principal,
                                     @Valid @RequestBody PayoutAccountRequest request) {
        return service.set(principal.userId(), request.provider(), request.msisdn());
    }

    @Operation(summary = "Confirm the number with the code sent to it")
    @PostMapping("/verify")
    public PayoutAccountResponse verify(@AuthenticationPrincipal AuthPrincipal principal,
                                        @Valid @RequestBody PayoutVerifyRequest request) {
        return service.verify(principal.userId(), request.code());
    }
}
