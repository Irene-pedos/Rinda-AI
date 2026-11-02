package com.rinda.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rinda.backend.auth.OtpSender;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Runs against real MySQL (database rinda_test), because plot geometry depends on its spatial functions. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ApiTestBase.TestOtp.class)
abstract class ApiTestBase {

    private static final List<String> TABLES = List.of("plot_status", "harvest_forecasts", "ml_runs", "baselines", "weather_daily", "observations",
            "claims", "enrollments", "seasons", "plots",
            "refresh_tokens", "otp_codes", "agents", "farmers", "users", "cooperatives");

    @TestConfiguration
    static class TestOtp {
        final Map<String, String> sent = new ConcurrentHashMap<>();

        @Bean
        OtpSender otpSender() {
            return sent::put;
        }
    }

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper mapper;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    private TestOtp otp;

    protected long cooperativeId;

    @BeforeEach
    void cleanDatabase() {
        TABLES.forEach(table -> jdbc.update("DELETE FROM " + table));
        otp.sent.clear();
        cooperativeId = newCooperative("Test Cooperative");
    }

    protected long newCooperative(String name) {
        jdbc.update("INSERT INTO cooperatives (name, district) VALUES (?, 'Test')", name);
        return jdbc.queryForObject("SELECT id FROM cooperatives WHERE name = ?", Long.class, name);
    }

    protected String lastCode(String phone) {
        return otp.sent.get(phone);
    }

    protected ResultActions postJson(String url, Object body) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)));
    }

    protected void requestOtp(String phone) throws Exception {
        postJson("/api/v1/auth/otp/request", Map.of("phone", phone)).andExpect(status().isAccepted());
    }

    /** Signs up a new farmer and returns the token response. */
    protected JsonNode signUp(String phone) throws Exception {
        requestOtp(phone);
        String body = postJson("/api/v1/auth/otp/verify", Map.of("phone", phone, "code", lastCode(phone),
                "fullName", "Test Farmer", "cooperativeId", cooperativeId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body);
    }

    protected String farmerToken(String phone) throws Exception {
        return signUp(phone).get("accessToken").asText();
    }

    /** Creates a staff account directly, as an admin would, and logs in with OTP. */
    protected String staffToken(String phone, String role, Long agentCooperativeId) throws Exception {
        jdbc.update("INSERT INTO users (phone, full_name, role) VALUES (?, 'Test Staff', ?)", phone, role);
        if (agentCooperativeId != null) {
            Long id = jdbc.queryForObject("SELECT id FROM users WHERE phone = ?", Long.class, phone);
            jdbc.update("INSERT INTO agents (user_id, cooperative_id) VALUES (?, ?)", id, agentCooperativeId);
        }
        requestOtp(phone);
        String body = postJson("/api/v1/auth/otp/verify", Map.of("phone", phone, "code", lastCode(phone)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("accessToken").asText();
    }
}
