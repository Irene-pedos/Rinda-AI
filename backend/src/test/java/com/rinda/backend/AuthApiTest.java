package com.rinda.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuthApiTest extends ApiTestBase {

    private static final String PHONE = "+250781111111";

    private Map<String, Object> verifyBody(String phone, String code) {
        Map<String, Object> body = new HashMap<>();
        body.put("phone", phone);
        body.put("code", code);
        return body;
    }

    private static String wrong(String code) {
        return code.equals("000000") ? "000001" : "000000";
    }

    @Test
    void newPhoneMustSendSignupDetailsAndTheCodeStaysValid() throws Exception {
        requestOtp(PHONE);
        String code = lastCode(PHONE);

        postJson("/api/v1/auth/otp/verify", verifyBody(PHONE, code))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SIGNUP_DETAILS_REQUIRED"));

        Map<String, Object> body = verifyBody(PHONE, code);
        body.put("fullName", "Aline");
        body.put("cooperativeId", cooperativeId);
        postJson("/api/v1/auth/otp/verify", body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("FARMER"))
                .andExpect(jsonPath("$.user.isDemo").value(false))
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM farmers", Long.class)).isEqualTo(1);
    }

    @Test
    void signupCannotChooseARole() throws Exception {
        requestOtp(PHONE);
        Map<String, Object> body = verifyBody(PHONE, lastCode(PHONE));
        body.put("fullName", "Aline");
        body.put("cooperativeId", cooperativeId);
        body.put("role", "ADMIN");
        postJson("/api/v1/auth/otp/verify", body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("FARMER"));
    }

    @Test
    void localPhoneFormatIsTheSameAccount() throws Exception {
        signUp(PHONE);
        requestOtpAfterResendWindow();
        postJson("/api/v1/auth/otp/verify", verifyBody("0781111111", lastCode(PHONE)))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class)).isEqualTo(1);
    }

    private void requestOtpAfterResendWindow() throws Exception {
        jdbc.update("UPDATE otp_codes SET created_at = created_at - INTERVAL 2 MINUTE");
        requestOtp(PHONE);
    }

    @Test
    void foreignPhoneIsRejected() throws Exception {
        postJson("/api/v1/auth/otp/request", Map.of("phone", "+15551234567"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PHONE_INVALID"));
    }

    @Test
    void codeIsStoredHashed() throws Exception {
        requestOtp(PHONE);
        String stored = jdbc.queryForObject("SELECT code_hash FROM otp_codes", String.class);
        assertThat(stored).isNotEqualTo(lastCode(PHONE)).startsWith("$2");
    }

    @Test
    void codeCanOnlyBeUsedOnce() throws Exception {
        signUp(PHONE);
        postJson("/api/v1/auth/otp/verify", verifyBody(PHONE, lastCode(PHONE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OTP_INVALID"));
    }

    @Test
    void codeLocksAfterTooManyWrongAttempts() throws Exception {
        signUp(PHONE);
        requestOtpAfterResendWindow();
        String code = lastCode(PHONE);
        for (int i = 0; i < 5; i++) {
            postJson("/api/v1/auth/otp/verify", verifyBody(PHONE, wrong(code)))
                    .andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("SELECT MAX(attempts) FROM otp_codes", Integer.class)).isEqualTo(5);
        postJson("/api/v1/auth/otp/verify", verifyBody(PHONE, code))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OTP_INVALID"));
    }

    @Test
    void expiredCodeIsRejected() throws Exception {
        requestOtp(PHONE);
        jdbc.update("UPDATE otp_codes SET expires_at = UTC_TIMESTAMP(6) - INTERVAL 1 SECOND");
        postJson("/api/v1/auth/otp/verify", verifyBody(PHONE, lastCode(PHONE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OTP_INVALID"));
    }

    @Test
    void resendingTooSoonIsRateLimited() throws Exception {
        requestOtp(PHONE);
        postJson("/api/v1/auth/otp/request", Map.of("phone", PHONE))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("OTP_RATE_LIMITED"));
    }

    @Test
    void refreshTokenRotatesAndReuseEndsAllSessions() throws Exception {
        JsonNode tokens = signUp(PHONE);
        String first = tokens.get("refreshToken").asText();

        String body = postJson("/api/v1/auth/refresh", Map.of("refreshToken", first))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String second = mapper.readTree(body).get("refreshToken").asText();
        assertThat(second).isNotEqualTo(first);

        postJson("/api/v1/auth/refresh", Map.of("refreshToken", first))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_INVALID"));
        postJson("/api/v1/auth/refresh", Map.of("refreshToken", second))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshTokenIsStoredHashed() throws Exception {
        String token = signUp(PHONE).get("refreshToken").asText();
        assertThat(jdbc.queryForObject("SELECT token_hash FROM refresh_tokens", String.class)).isNotEqualTo(token);
    }

    @Test
    void suspendedAccountCannotLogIn() throws Exception {
        signUp(PHONE);
        jdbc.update("UPDATE users SET status = 'SUSPENDED'");
        requestOtpAfterResendWindow();
        postJson("/api/v1/auth/otp/verify", verifyBody(PHONE, lastCode(PHONE)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
    }

    @Test
    void protectedEndpointsNeedAValidToken() throws Exception {
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer not-a-token"))
                .andExpect(status().isUnauthorized());

        String token = farmerToken(PHONE);
        String tampered = token.substring(0, token.length() - 3) + "abc";
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value(PHONE));
    }

    @Test
    void cooperativesArePublicForTheSignupScreen() throws Exception {
        mvc.perform(get("/api/v1/auth/cooperatives"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Test Cooperative"));
    }

    @Test
    void openApiDocumentIsServed() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/plots']").exists());
    }
}
