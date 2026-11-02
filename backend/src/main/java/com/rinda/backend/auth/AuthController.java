package com.rinda.backend.auth;

import com.rinda.backend.auth.AuthDtos.OtpRequest;
import com.rinda.backend.auth.AuthDtos.OtpVerifyRequest;
import com.rinda.backend.auth.AuthDtos.RefreshRequest;
import com.rinda.backend.auth.AuthDtos.TokenResponse;
import com.rinda.backend.user.CooperativeRepository;
import com.rinda.backend.user.CooperativeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth")
@SecurityRequirements
public class AuthController {

    private final AuthService authService;
    private final CooperativeRepository cooperatives;

    public AuthController(AuthService authService, CooperativeRepository cooperatives) {
        this.authService = authService;
        this.cooperatives = cooperatives;
    }

    @Operation(summary = "Send a login code. In dev mode it is printed in the backend log.")
    @PostMapping("/otp/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestOtp(@Valid @RequestBody OtpRequest request) {
        authService.requestOtp(request.phone());
    }

    @Operation(summary = "Log in, or sign up as a farmer",
            description = "A new phone number gets 409 SIGNUP_DETAILS_REQUIRED. Resend with fullName and cooperativeId; the code stays valid.")
    @PostMapping("/otp/verify")
    public TokenResponse verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        return authService.verifyOtp(request);
    }

    @Operation(summary = "Swap a refresh token for new tokens. Each refresh token works once.",
            description = "Sending a used token again ends all of the user's sessions, so clients must never refresh twice at once.")
    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @Operation(summary = "List cooperatives for the sign-up screen")
    @GetMapping("/cooperatives")
    public List<CooperativeResponse> cooperatives() {
        return cooperatives.findAll(Sort.by("name")).stream().map(CooperativeResponse::of).toList();
    }
}
