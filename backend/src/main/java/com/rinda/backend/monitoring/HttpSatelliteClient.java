package com.rinda.backend.monitoring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rinda.backend.analysis.MlClient.MlCallException;
import com.rinda.backend.config.RindaProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** The ML service's /monitor/plot over HTTP, with the shared internal API key. */
@Component
public class HttpSatelliteClient implements SatelliteClient {

    private final RestClient http;
    private final ObjectMapper mapper;

    public HttpSatelliteClient(RindaProperties properties, ObjectMapper mapper) {
        this.mapper = mapper;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        factory.setReadTimeout(properties.ml().timeout());
        this.http = RestClient.builder()
                .baseUrl(properties.ml().baseUrl())
                .requestFactory(factory)
                .defaultHeader("X-Internal-Api-Key", properties.ml().internalApiKey())
                .build();
    }

    @Override
    public JsonNode monitor(MonitorRequest request) {
        ObjectNode body = mapper.createObjectNode();
        body.put("plotId", request.plotId());
        body.put("source", request.source().toLowerCase());
        if (request.boundary() != null) {
            body.set("boundary", request.boundary());
        }
        putDate(body, "plantingDate", request.plantingDate());
        putDate(body, "fetchFrom", request.fetchFrom());
        putDate(body, "asOf", request.asOf());
        if (request.minClearPixels() != null) {
            body.put("minClearPixels", request.minClearPixels());
        }
        ArrayNode history = body.putArray("history");
        for (HistoryRow row : request.history()) {
            ObjectNode item = history.addObject();
            item.put("date", row.date().toString());
            item.put("ndviMedian", row.ndviMedian());
            item.put("pixelCount", row.pixelCount());
            item.put("cloudFraction", row.cloudFraction());
        }
        try {
            return http.post().uri("/monitor/plot").contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().body(JsonNode.class);
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            String detail = ex.getResponseBodyAsString();
            // 503 = Earth Engine not available, 5xx = a failure that may pass: retried. 4xx is a refusal.
            throw new MlCallException(status, detail == null || detail.isBlank() ? ex.getStatusText() : detail,
                    status >= 500);
        } catch (ResourceAccessException ex) {
            throw new MlCallException(503, "Could not reach the ML service: " + ex.getMessage(), true);
        }
    }

    private static void putDate(ObjectNode body, String field, java.time.LocalDate value) {
        if (value != null) {
            body.put(field, value.toString());
        }
    }
}
