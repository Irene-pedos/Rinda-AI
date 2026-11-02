package com.rinda.backend.auth;

import com.rinda.backend.user.User;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record OtpRequest(@NotBlank String phone) {
    }

    /** fullName and cooperativeId are only needed the first time a phone number signs in. */
    public record OtpVerifyRequest(
            @NotBlank String phone,
            @NotBlank @Pattern(regexp = "\\d{6}", message = "must be 6 digits") String code,
            @Size(max = 120) String fullName,
            Long cooperativeId) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record TokenResponse(String accessToken,
                                @Schema(description = "Seconds until the access token expires")
                                long accessTokenExpiresIn,
                                String refreshToken, UserResponse user) {
    }

    public record UserResponse(long id, String phone, String fullName, Role role,
                               @Schema(allowableValues = {"rw", "en"}) String language,
                               @Schema(description = "When true, every UI must show a Demo label") boolean isDemo) {

        public static UserResponse of(User user) {
            return new UserResponse(user.getId(), user.getPhone(), user.getFullName(), user.getRole(),
                    user.getLanguage(), user.isDemo());
        }
    }
}
