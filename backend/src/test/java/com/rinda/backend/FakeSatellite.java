package com.rinda.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rinda.backend.analysis.MlClient.MlCallException;
import com.rinda.backend.monitoring.SatelliteClient;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Stands in for the ML service's /monitor/plot in every test, so no test reaches Earth Engine. By default it
 * finds no image; a test can queue invented observations, a season flag, or failures. Fake data only.
 */
public class FakeSatellite implements SatelliteClient {

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        FakeSatellite fakeSatellite() {
            return new FakeSatellite();
        }
    }

    public record FakeImage(LocalDate date, Double ndvi, int pixels) {
    }

    private final ObjectMapper mapper = new ObjectMapper();
    final List<MonitorRequest> calls = new CopyOnWriteArrayList<>();
    final Deque<RuntimeException> failures = new ArrayDeque<>();
    List<FakeImage> images = new ArrayList<>();
    String flag = "NOT_ENOUGH_DATA";
    String reason = "FEW_CLEAR_IMAGES";

    void reset() {
        calls.clear();
        failures.clear();
        images = new ArrayList<>();
        flag = "NOT_ENOUGH_DATA";
        reason = "FEW_CLEAR_IMAGES";
    }

    @Override
    public JsonNode monitor(MonitorRequest request) {
        calls.add(request);
        if (!failures.isEmpty()) {
            throw failures.poll();
        }
        boolean sample = "SAMPLE".equals(request.source());
        LocalDate planting = sample ? LocalDate.of(2025, 9, 20) : request.plantingDate();
        LocalDate from = request.fetchFrom() != null ? request.fetchFrom() : planting;
        LocalDate to = sample ? LocalDate.of(2025, 12, 10) : request.asOf();
        ObjectNode body = mapper.createObjectNode();
        body.put("plotId", request.plotId());
        body.put("dataOrigin", sample ? "SAMPLE_FILE" : "GEE");
        body.put("modelVersion", "0.6.0-test");
        body.put("plantingDate", planting.toString());
        body.put("fetchFrom", from.toString());
        body.put("fetchTo", to.toString());
        body.putObject("params").put("ndviReducer", "median");
        ArrayNode observations = body.putArray("observations");
        ArrayNode rainfall = body.putArray("rainfall");
        LocalDate latest = null;
        for (FakeImage image : images) {
            if (image.date().isBefore(from) || image.date().isAfter(to)) {
                continue;
            }
            ObjectNode row = observations.addObject();
            row.put("date", image.date().toString());
            row.put("source", "S2");
            row.put("ndviMedian", image.ndvi());
            row.put("pixelCount", image.pixels());
            row.put("cloudFraction", 0.1);
            rainfall.addObject().put("date", image.date().toString()).put("rainMm", 3.5).put("source", "CHIRPS");
            latest = image.date();
        }
        ObjectNode check = body.putObject("seasonCheck");
        check.put("checkDate", to.toString());
        check.put("flag", flag);
        check.put("reason", reason);
        check.put("confidence", "LOW");
        check.putArray("signals");
        check.put("dataQualityNote", "Fake data for tests.");
        check.put("imagesUsed", images.size());
        check.putNull("pixelsUsed");
        if (latest == null) {
            check.putNull("latestImageDate");
        } else {
            check.put("latestImageDate", latest.toString());
        }
        return body;
    }

    static MlCallException earthEngineDown() {
        return new MlCallException(503, "Earth Engine is not available", true);
    }
}
