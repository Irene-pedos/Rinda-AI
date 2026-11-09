package com.rinda.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.rinda.backend.analysis.AnalysisService;
import com.rinda.backend.analysis.MlClient;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** "Run analysis" (phase 4), against a fake ML service. Runs execute inline in tests (rinda.analysis.run-inline). */
@Import(AnalysisRunApiTest.FakeMlConfig.class)
class AnalysisRunApiTest extends ApiTestBase {

    private static final List<List<Double>> SQUARE = List.of(List.of(30.100, -1.950), List.of(30.102, -1.950),
            List.of(30.102, -1.948), List.of(30.100, -1.948), List.of(30.100, -1.950));

    /** Records each call and plays back scripted outcomes per plot (default: success). */
    static class FakeMl implements MlClient {
        final List<PlotJob> calls = new ArrayList<>();
        final Map<Long, Deque<RuntimeException>> failures = new HashMap<>();
        boolean healthDown;

        @Override
        public Processed process(PlotJob job) {
            calls.add(job);
            Deque<RuntimeException> queue = failures.get(job.plotId());
            if (queue != null && !queue.isEmpty()) {
                throw queue.poll();
            }
            return new Processed(77, job.source(), "0.3.0");
        }

        @Override
        public JsonNode health() {
            if (healthDown) {
                throw new MlCallException(503, "Could not reach the ML service", true);
            }
            return JsonNodeFactory.instance.objectNode().put("status", "ok").put("modelVersion", "0.3.0");
        }
    }

    @TestConfiguration
    static class FakeMlConfig {
        @Bean
        @Primary
        FakeMl fakeMl() {
            return new FakeMl();
        }
    }

    @Autowired
    private FakeMl ml;
    @Autowired
    private AnalysisService analysis;

    private String admin;
    private String farmer;
    private long seasonId;

    @BeforeEach
    void setUp() throws Exception {
        ml.calls.clear();
        ml.failures.clear();
        ml.healthDown = false;
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
        jdbc.update("DELETE FROM analysis_run_items");
        jdbc.update("DELETE FROM analysis_runs");
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 1");
        admin = staffToken("+250789999991", "ADMIN", null);
        farmer = farmerToken("+250789999992");
        jdbc.update("INSERT INTO seasons (code, type) VALUES ('2026A', 'A')");
        seasonId = jdbc.queryForObject("SELECT id FROM seasons WHERE code = '2026A'", Long.class);
    }

    private long plot(String name, String status, boolean covered) throws Exception {
        Map<String, Object> plot = Map.of("name", name, "plantingDate", "2026-09-20",
                "boundary", Map.of("type", "Polygon", "coordinates", List.of(SQUARE)));
        String body = mvc.perform(post("/api/v1/plots").header("Authorization", "Bearer " + farmer)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(plot)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = mapper.readTree(body).get("id").asLong();
        jdbc.update("UPDATE plots SET status = ? WHERE id = ?", status, id);
        if (covered) {
            jdbc.update("INSERT INTO enrollments (plot_id, season_id, status) VALUES (?, ?, 'COVERED')", id, seasonId);
        }
        return id;
    }

    private ResultActions run(Object body) throws Exception {
        return mvc.perform(post("/api/v1/admin/analysis-runs").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content(body == null ? "" : mapper.writeValueAsString(body)));
    }

    private long runId(ResultActions result) throws Exception {
        return mapper.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void runAnalysisCoversEveryVerifiedRealPlotWithItsDetails() throws Exception {
        long ready = plot("Ready", "ACTIVE", true);
        plot("Waiting for check", "PENDING_VERIFICATION", true);
        long demo = plot("Demo", "ACTIVE", true);
        jdbc.update("UPDATE plots SET is_demo = TRUE WHERE id = ?", demo);

        long id = runId(run(null).andExpect(status().isAccepted()));
        assertThat(ml.calls).hasSize(1);
        MlClient.PlotJob job = ml.calls.get(0);
        assertThat(job.plotId()).isEqualTo(ready);
        assertThat(job.source()).isEqualTo("GEE");
        assertThat(job.seasonType()).isEqualTo("A");
        assertThat(job.plantingDate()).hasToString("2026-09-20");
        assertThat(job.boundary().path("type").asText()).isEqualTo("Polygon");

        mvc.perform(get("/api/v1/admin/analysis-runs/" + id).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.plotsTotal").value(1))
                .andExpect(jsonPath("$.plotsDone").value(1))
                .andExpect(jsonPath("$.items[0].status").value("DONE"))
                .andExpect(jsonPath("$.items[0].mlRunId").value(77));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action = 'ANALYSIS_RUN_STARTED'",
                Long.class)).isEqualTo(1);
    }

    @Test
    void namedPlotsThatCannotBeAnalysedAreSkippedWithAReason() throws Exception {
        long pending = plot("Pending", "PENDING_VERIFICATION", true);
        long noSeason = plot("No season", "ACTIVE", false);
        long unconfirmed = plot("Unconfirmed", "ACTIVE", true);
        jdbc.update("UPDATE plots SET data_status = 'TO_CONFIRM' WHERE id = ?", unconfirmed);

        long id = runId(run(Map.of("plotIds", List.of(pending, noSeason, unconfirmed))));
        assertThat(ml.calls).isEmpty();
        mvc.perform(get("/api/v1/admin/analysis-runs/" + id).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.plotsSkipped").value(3))
                .andExpect(jsonPath("$.items[0].errorCode").value("PLOT_NOT_ACTIVE"))
                .andExpect(jsonPath("$.items[1].errorCode").value("NO_SEASON"))
                .andExpect(jsonPath("$.items[2].errorCode").value("PLOT_NOT_CONFIRMED"));
    }

    @Test
    void theSimulatedSampleOnlyGoesOnDemoPlots() throws Exception {
        long real = plot("Real", "ACTIVE", true);
        long demo = plot("Demo", "ACTIVE", true);
        long demoPending = plot("Demo pending", "PENDING_VERIFICATION", true);
        jdbc.update("UPDATE plots SET is_demo = TRUE WHERE id IN (?, ?)", demo, demoPending);

        run(Map.of("source", "SAMPLE")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLOT_IDS_REQUIRED"));
        long id = runId(run(Map.of("source", "SAMPLE", "plotIds", List.of(real, demo, demoPending))));
        assertThat(ml.calls).extracting(MlClient.PlotJob::plotId).containsExactly(demo);
        assertThat(ml.calls.get(0).source()).isEqualTo("SAMPLE");
        mvc.perform(get("/api/v1/admin/analysis-runs/" + id).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.items[0].errorCode").value("REAL_PLOT"))
                .andExpect(jsonPath("$.items[1].status").value("DONE"))
                .andExpect(jsonPath("$.items[2].errorCode").value("PLOT_NOT_ACTIVE")); // not verified yet
    }

    @Test
    void serviceFailuresAreRetriedButRefusalsAreNot() throws Exception {
        long flaky = plot("Flaky", "ACTIVE", true);
        long refused = plot("Refused", "ACTIVE", true);
        ml.failures.put(flaky, new ArrayDeque<>(List.of(
                new MlClient.MlCallException(503, "Earth Engine is not available", true))));
        ml.failures.put(refused, new ArrayDeque<>(List.of(
                new MlClient.MlCallException(409, "{\"code\":\"PLOT_NOT_CONFIRMED\"}", false))));

        long id = runId(run(null));
        mvc.perform(get("/api/v1/admin/analysis-runs/" + id).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.plotsDone").value(1))
                .andExpect(jsonPath("$.plotsFailed").value(1))
                .andExpect(jsonPath("$.items[0].status").value("DONE"))
                .andExpect(jsonPath("$.items[0].attempts").value(2))
                .andExpect(jsonPath("$.items[1].status").value("FAILED"))
                .andExpect(jsonPath("$.items[1].attempts").value(1))
                .andExpect(jsonPath("$.items[1].errorCode").value("REFUSED"));
    }

    @Test
    void onlyAdminsRunAnalysis() throws Exception {
        String officer = staffToken("+250789999993", "OFFICER", null);
        mvc.perform(post("/api/v1/admin/analysis-runs").header("Authorization", "Bearer " + officer))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/analysis-runs").header("Authorization", "Bearer " + farmer))
                .andExpect(status().isForbidden());
    }

    @Test
    void onlyOneRunAtATime() throws Exception {
        jdbc.update("INSERT INTO analysis_runs (source, status, trigger_type) VALUES ('GEE', 'RUNNING', 'MANUAL')");
        run(null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ANALYSIS_ALREADY_RUNNING"));
    }

    @Test
    void aRunCutOffByARestartIsMarkedInterrupted() throws Exception {
        long plotId = plot("Field", "ACTIVE", true);
        jdbc.update("INSERT INTO analysis_runs (source, status, trigger_type) VALUES ('GEE', 'RUNNING', 'MANUAL')");
        long id = jdbc.queryForObject("SELECT id FROM analysis_runs", Long.class);
        jdbc.update("INSERT INTO analysis_run_items (run_id, plot_id, status) VALUES (?, ?, 'RUNNING')", id, plotId);

        analysis.markInterruptedRuns();
        assertThat(jdbc.queryForObject("SELECT status FROM analysis_runs", String.class)).isEqualTo("INTERRUPTED");
        assertThat(jdbc.queryForObject("SELECT error_code FROM analysis_run_items", String.class))
                .isEqualTo("INTERRUPTED");
        run(null).andExpect(status().isAccepted()); // a new run can start
    }

    @Test
    void mlHealthSaysWhenTheServiceIsDown() throws Exception {
        mvc.perform(get("/api/v1/admin/analysis-runs/ml-health").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.reachable").value(true))
                .andExpect(jsonPath("$.modelVersion").value("0.3.0"));
        ml.healthDown = true;
        mvc.perform(get("/api/v1/admin/analysis-runs/ml-health").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reachable").value(false));
    }
}
