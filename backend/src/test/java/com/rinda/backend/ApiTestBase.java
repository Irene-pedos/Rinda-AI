package com.rinda.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rinda.backend.auth.OtpSender;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
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
@Import({ApiTestBase.TestOtp.class, FakeSatellite.Config.class})
abstract class ApiTestBase {

    private static final List<String> TABLES = List.of("notifications", "audit_log", "model_versions", "plot_status", "harvest_forecasts", "ml_runs", "baselines", "weather_daily", "observations",
            "season_checks", "satellite_observations", "satellite_rainfall", "satellite_jobs",
            "analysis_run_items", "analysis_runs", "payouts", "payout_accounts", "claim_decisions", "review_requests", "problem_reports", "field_visits",
            "plot_verifications", "plot_flags", "gps_walk_points", "gps_walks", "plot_documents",
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
    @Autowired
    protected FakeSatellite satellite;

    protected long cooperativeId;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
        TABLES.forEach(table -> jdbc.update("DELETE FROM " + table));
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 1");
        otp.sent.clear();
        satellite.reset();
        cooperativeId = newCooperative("Test Cooperative");
    }

    protected long newCooperative(String name) {
        jdbc.update("INSERT INTO cooperatives (name, district) VALUES (?, 'Test')", name);
        return jdbc.queryForObject("SELECT id FROM cooperatives WHERE name = ?", Long.class, name);
    }

    /** thresholds is not cleaned between tests, so the registration limits go back to "not supplied". */
    @AfterEach
    void resetRegistrationThresholds() {
        jdbc.update("""
                UPDATE thresholds SET threshold_value = NULL, status = 'TO_CONFIRM'
                WHERE threshold_key IN ('walk_max_accuracy_m', 'plot_area_mismatch_pct',
                                        'verification_photo_max_distance_m')
                """);
    }

    protected void setThreshold(String key, Number value) {
        jdbc.update("UPDATE thresholds SET threshold_value = ? WHERE threshold_key = ?", value, key);
    }

    // ===== Plot registration (fake data only: zero-code UPIs, invented coordinates) =====

    protected ResultActions send(String token, MockHttpServletRequestBuilder request, Object body) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)));
    }

    protected Map<String, Object> registration(String name, String plantingDate, String upi) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("upi", upi);
        body.put("plantingDate", plantingDate);
        body.put("relationshipToPlot", "OWNER");
        body.put("declarationAccepted", true);
        return body;
    }

    /** Step 1 only: details, no boundary yet (DRAFT). */
    protected long draftPlot(String token, String name, String plantingDate) throws Exception {
        String body = send(token, post("/api/v1/plots"),
                        registration(name, plantingDate, "0/00/00/00/" + UPI_SEQ.incrementAndGet()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("id").asLong();
    }

    /** Corners of a square about 222 m wide, placed by plot id so test plots never overlap by accident. */
    protected static List<double[]> squareFor(long plotId) {
        double lon = 30.10 + (plotId % 200) * 0.003;
        double lat = -1.95;
        return List.of(new double[] {lon, lat}, new double[] {lon + 0.002, lat},
                new double[] {lon + 0.002, lat + 0.002}, new double[] {lon, lat + 0.002});
    }

    protected Map<String, Object> walkBody(List<double[]> corners, double accuracyM, boolean mock) {
        Instant start = Instant.now().minusSeconds(600);
        List<Map<String, Object>> points = new ArrayList<>();
        for (int i = 0; i < corners.size(); i++) {
            points.add(Map.of("lng", corners.get(i)[0], "lat", corners.get(i)[1], "accuracyM", accuracyM,
                    "recordedAt", start.plusSeconds(60L * i).toString(), "mockLocation", mock));
        }
        return Map.of("points", points);
    }

    protected ResultActions walk(String token, long plotId, List<double[]> corners) throws Exception {
        return send(token, post("/api/v1/plots/" + plotId + "/walk"), walkBody(corners, 4.0, false));
    }

    protected ResultActions uploadETitle(String token, long plotId, Object areaM2) throws Exception {
        return mvc.perform(multipart("/api/v1/plots/" + plotId + "/documents")
                .file(new MockMultipartFile("file", "etitle.png", "image/png", FAKE_PNG))
                .param("type", "E_TITLE").param("documentAreaM2", String.valueOf(areaM2))
                .header("Authorization", "Bearer " + token));
    }

    /**
     * A fully registered plot waiting for the agent: details, a GPS walk and an e-title whose typed area matches
     * the walk. Sets the accuracy and area limits for this test so no review flag is raised.
     */
    protected long pendingPlot(String token, String name, String plantingDate) throws Exception {
        setThreshold("walk_max_accuracy_m", 10);
        setThreshold("plot_area_mismatch_pct", 15);
        long id = draftPlot(token, name, plantingDate);
        String walked = walk(token, id, squareFor(id)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        uploadETitle(token, id, mapper.readTree(walked).get("walkedAreaM2").decimalValue())
                .andExpect(status().isCreated());
        return id;
    }

    /** The agent's field check, as the app sends it. A null photo sends no photo part. */
    protected ResultActions fieldCheck(String token, long plotId, Map<String, String> params, byte[] photo)
            throws Exception {
        var request = multipart("/api/v1/agent/plots/" + plotId + "/verify");
        params.forEach(request::param);
        if (photo != null) {
            request.file(new MockMultipartFile("photo", "plot.jpg", "image/jpeg", photo));
        }
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }

    /** Verify params with a photo location in the middle of the walked square. */
    protected static Map<String, String> verifyAtPlot(long plotId) {
        double[] corner = squareFor(plotId).get(0);
        Map<String, String> params = new HashMap<>();
        params.put("decision", "ACTIVE");
        params.put("photoLat", String.valueOf(corner[1] + 0.001));
        params.put("photoLng", String.valueOf(corner[0] + 0.001));
        params.put("photoAccuracyM", "5");
        params.put("photoTakenAt", Instant.now().minusSeconds(60).toString());
        return params;
    }

    protected static final byte[] FAKE_JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F'};

    /** As if an agent had checked the plot in the field. */
    protected void fieldVerified(long plotId) {
        jdbc.update("UPDATE plots SET status = 'ACTIVE', verification_level = 'FIELD_VERIFIED' WHERE id = ?", plotId);
    }

    protected static final byte[] FAKE_PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
    private static final AtomicInteger UPI_SEQ = new AtomicInteger();

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
