package com.rinda.backend.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rinda.backend.config.RindaProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** The real ML service over HTTP, authenticated with the shared internal API key. */
@Component
public class HttpMlClient implements MlClient {

    private final RestClient http;
    private final ObjectMapper mapper;

    public HttpMlClient(RindaProperties properties, ObjectMapper mapper) {
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
    public Processed process(PlotJob job) {
        ObjectNode body = mapper.createObjectNode();
        body.put("plotId", job.plotId());
        body.put("source", job.source().toLowerCase());
        if (job.boundary() != null) {
            body.set("boundary", job.boundary());
        }
        if (job.plantingDate() != null) {
            body.put("plantingDate", job.plantingDate().toString());
        }
        if (job.seasonType() != null) {
            body.put("seasonType", job.seasonType());
        }
        if (job.asOf() != null) {
            body.put("asOf", job.asOf().toString());
        }
        JsonNode answer = call(() -> http.post().uri("/process/plot").contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().body(JsonNode.class));
        return new Processed(answer.path("saved").path("runId").asLong(), answer.path("dataOrigin").asText(null),
                answer.path("modelVersion").asText(null));
    }

    @Override
    public JsonNode health() {
        return call(() -> http.get().uri("/health").retrieve().body(JsonNode.class));
    }

    private interface Call {
        JsonNode run();
    }

    private static JsonNode call(Call call) {
        try {
            return call.run();
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            // 4xx = the ML service or the backend refused this plot (for example PLOT_NOT_CONFIRMED). Not retried.
            // 503 = Earth Engine not available; 5xx/502 = a failure that may pass. Retried.
            return fail(status, detail(ex), status >= 500);
        } catch (ResourceAccessException ex) {
            return fail(503, "Could not reach the ML service: " + ex.getMessage(), true);
        }
    }

    private static JsonNode fail(int status, String message, boolean retryable) {
        throw new MlCallException(status, message, retryable);
    }

    private static String detail(RestClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        return body == null || body.isBlank() ? ex.getStatusText() : body;
    }
}
