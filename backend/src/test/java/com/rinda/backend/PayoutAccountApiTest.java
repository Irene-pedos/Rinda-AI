package com.rinda.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** The farmer's mobile-money number for simulated payouts (spec §2.2 #12). */
class PayoutAccountApiTest extends ApiTestBase {

    private static final String NUMBER = "+250783333333";

    private String farmer;

    @BeforeEach
    void farmer() throws Exception {
        farmer = farmerToken("+250787777771");
    }

    private ResultActions set(String provider, String msisdn) throws Exception {
        return mvc.perform(post("/api/v1/payout-account").header("Authorization", "Bearer " + farmer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("provider", provider, "msisdn", msisdn))));
    }

    private ResultActions verify(String code) throws Exception {
        return mvc.perform(post("/api/v1/payout-account/verify").header("Authorization", "Bearer " + farmer)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("code", code))));
    }

    @Test
    void noAccountYetIsA404() throws Exception {
        mvc.perform(get("/api/v1/payout-account").header("Authorization", "Bearer " + farmer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYOUT_ACCOUNT_NOT_SET"));
    }

    @Test
    void aNewNumberIsUnverifiedUntilTheCodeSentToItIsEntered() throws Exception {
        set("MTN_MOMO", "0783333333").andExpect(status().isAccepted())
                .andExpect(jsonPath("$.msisdn").value(NUMBER))
                .andExpect(jsonPath("$.verified").value(false))
                .andExpect(jsonPath("$.nameCheck").value("SIMULATED"));
        verify("000000".equals(lastCode(NUMBER)) ? "111111" : "000000")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("OTP_INVALID"));
        verify(lastCode(NUMBER)).andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.nameMatch").value(true));
    }

    @Test
    void changingTheNumberNeedsANewVerification() throws Exception {
        set("MTN_MOMO", NUMBER);
        verify(lastCode(NUMBER));
        set("MTN_MOMO", NUMBER).andExpect(jsonPath("$.verified").value(true)); // same number stays verified

        set("AIRTEL", "+250733333333").andExpect(jsonPath("$.verified").value(false));
        mvc.perform(get("/api/v1/payout-account").header("Authorization", "Bearer " + farmer))
                .andExpect(jsonPath("$.provider").value("AIRTEL"))
                .andExpect(jsonPath("$.verified").value(false));
    }

    @Test
    void theNumberMustBelongToTheProvider() throws Exception {
        set("AIRTEL", NUMBER).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYOUT_PROVIDER_MISMATCH"));
    }

    @Test
    void aLoginCodeCannotVerifyAPayoutNumber() throws Exception {
        set("MTN_MOMO", NUMBER);
        String payoutCode = lastCode(NUMBER);
        requestOtp(NUMBER); // a login code for the same number
        String loginCode = lastCode(NUMBER);
        if (!loginCode.equals(payoutCode)) {
            verify(loginCode).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("OTP_INVALID"));
        }
        verify(payoutCode).andExpect(status().isOk());
    }

    @Test
    void onlyFarmersHaveAPayoutAccount() throws Exception {
        String officer = staffToken("+250787777772", "OFFICER", null);
        mvc.perform(get("/api/v1/payout-account").header("Authorization", "Bearer " + officer))
                .andExpect(status().isForbidden());
    }
}
