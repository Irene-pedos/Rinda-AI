package com.rinda.backend.auth;

import com.rinda.backend.auth.AuthDtos.OtpVerifyRequest;
import com.rinda.backend.auth.AuthDtos.TokenResponse;
import com.rinda.backend.auth.AuthDtos.UserResponse;
import com.rinda.backend.common.ApiException;
import com.rinda.backend.config.RindaProperties;
import com.rinda.backend.user.CooperativeRepository;
import com.rinda.backend.user.Farmer;
import com.rinda.backend.user.FarmerRepository;
import com.rinda.backend.user.User;
import com.rinda.backend.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String PHONE_RULE = "\\+2507[2389]\\d{7}";

    private final OtpService otpService;
    private final JwtService jwtService;
    private final UserRepository users;
    private final FarmerRepository farmers;
    private final CooperativeRepository cooperatives;
    private final RefreshTokenRepository refreshTokens;
    private final RindaProperties.Jwt jwtConfig;
    private final SecureRandom random = new SecureRandom();

    public AuthService(OtpService otpService, JwtService jwtService, UserRepository users, FarmerRepository farmers,
                       CooperativeRepository cooperatives, RefreshTokenRepository refreshTokens,
                       RindaProperties properties) {
        this.otpService = otpService;
        this.jwtService = jwtService;
        this.users = users;
        this.farmers = farmers;
        this.cooperatives = cooperatives;
        this.refreshTokens = refreshTokens;
        this.jwtConfig = properties.jwt();
    }

    public void requestOtp(String rawPhone) {
        otpService.request(normalise(rawPhone));
    }

    @Transactional(noRollbackFor = ApiException.class)
    public TokenResponse verifyOtp(OtpVerifyRequest request) {
        String phone = normalise(request.phone());
        OtpCode otp = otpService.check(phone, request.code());

        Optional<User> existing = users.findByPhone(phone);
        if (existing.isEmpty()) {
            requireSignupDetails(request);
        }
        otpService.consume(otp);

        User user = existing.orElseGet(() -> createFarmer(phone, request));
        if (!user.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_SUSPENDED", "This account is suspended.");
        }
        return issueTokens(user);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public TokenResponse refresh(String rawToken) {
        Instant now = Instant.now();
        RefreshToken token = refreshTokens.findByTokenHash(hash(rawToken)).orElseThrow(AuthService::refreshInvalid);

        if (token.getRevokedAt() != null) {
            // A used token coming back means it may have been stolen, so end every session.
            refreshTokens.revokeAllForUser(token.getUserId(), now);
            throw refreshInvalid();
        }
        if (!token.getExpiresAt().isAfter(now) || refreshTokens.revoke(token.getId(), now) != 1) {
            throw refreshInvalid();
        }

        User user = users.findById(token.getUserId()).filter(User::isActive).orElseThrow(AuthService::refreshInvalid);
        return issueTokens(user);
    }

    private void requireSignupDetails(OtpVerifyRequest request) {
        if (request.fullName() == null || request.fullName().isBlank() || request.cooperativeId() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "SIGNUP_DETAILS_REQUIRED",
                    "New account: send fullName and cooperativeId with the same code.");
        }
        if (!cooperatives.existsById(request.cooperativeId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "COOPERATIVE_NOT_FOUND", "That cooperative does not exist.");
        }
    }

    /** Sign-up only ever creates farmers. Agents, officers and admins are created by an admin. */
    private User createFarmer(String phone, OtpVerifyRequest request) {
        User user = users.save(new User(phone, request.fullName().trim(), Role.FARMER));
        farmers.save(new Farmer(user.getId(), request.cooperativeId()));
        return user;
    }

    private TokenResponse issueTokens(User user) {
        Instant now = Instant.now();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokens.save(new RefreshToken(user.getId(), hash(refreshToken), now.plus(jwtConfig.refreshTtl()), now));
        return new TokenResponse(jwtService.issueAccessToken(user.getId(), user.getRole()),
                jwtService.accessTtlSeconds(), refreshToken, UserResponse.of(user));
    }

    /** Accepts 0781234567 or +250781234567 and stores the second form, so one phone is one account. */
    public static String normalise(String rawPhone) {
        String phone = rawPhone.replaceAll("[\\s-]", "");
        if (phone.startsWith("07")) {
            phone = "+250" + phone.substring(1);
        }
        if (!phone.matches(PHONE_RULE)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PHONE_INVALID", "Use a Rwandan mobile number.");
        }
        return phone;
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static ApiException refreshInvalid() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_INVALID", "Please log in again.");
    }
}
