package com.rinda.backend.user;

import com.rinda.backend.auth.AuthDtos.UserResponse;
import com.rinda.backend.auth.AuthPrincipal;
import com.rinda.backend.common.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "Me")
public class MeController {

    private final UserRepository users;

    public MeController(UserRepository users) {
        this.users = users;
    }

    @Operation(summary = "Get the logged-in user, including their role")
    @GetMapping
    public UserResponse me(@AuthenticationPrincipal AuthPrincipal principal) {
        return users.findById(principal.userId()).map(UserResponse::of)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Please log in again."));
    }
}
