package com.rinda.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.rinda.backend.plot.PlotService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Holds the backend to what docs/API.md promises clients. */
@TestPropertySource(properties = "springdoc.api-docs.enabled=true")
class ApiContractTest extends ApiTestBase {

    private static final String PHONE = "+250783333331";
    private static final String API_PREFIX = "/api/v1";

    @MockitoBean
    private PlotService plots;

    private String bearer() throws Exception {
        return "Bearer " + farmerToken(PHONE);
    }

    private JsonNode apiDocs() throws Exception {
        String body = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body);
    }

    @Test
    void badPathValueHasACode() throws Exception {
        mvc.perform(get("/api/v1/plots/abc").header("Authorization", bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_INVALID"));
    }

    @Test
    void unknownRouteHasACode() throws Exception {
        mvc.perform(get("/api/v1/nothing").header("Authorization", bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void wrongMethodHasACode() throws Exception {
        mvc.perform(patch("/api/v1/plots/1").header("Authorization", bearer()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void wrongContentTypeHasACode() throws Exception {
        mvc.perform(post("/api/v1/plots").header("Authorization", bearer())
                        .contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void unexpectedFailureHasACodeAndHidesTheCause() throws Exception {
        when(plots.list(any(), anyInt(), anyInt())).thenThrow(new IllegalStateException("db password is hunter2"));

        String body = mvc.perform(get("/api/v1/plots").header("Authorization", bearer()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("hunter2");
    }

    @Test
    void wrongRoleIsStillA403NotA500() throws Exception {
        String officer = staffToken("+250783333332", "OFFICER", null);
        mvc.perform(post("/api/v1/plots").header("Authorization", "Bearer " + officer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"x","plantingDate":"2026-01-01",
                                 "boundary":{"type":"Polygon","coordinates":[]}}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void swaggerHidesInternalEndpointsAndDocumentsErrors() throws Exception {
        JsonNode paths = apiDocs().get("paths");

        assertThat(paths.has("/api/v1/internal/ml/results")).isFalse();
        operations(paths).forEach(operation -> assertThat(operation.at("/responses/default").isMissingNode())
                .as("every operation documents its errors").isFalse());
    }

    @Test
    void loginEndpointsNeedNoTokenInSwagger() throws Exception {
        JsonNode paths = apiDocs().get("paths");

        assertThat(paths.at("/~1api~1v1~1auth~1otp~1request/post/security")).isEmpty();
        assertThat(paths.at("/~1api~1v1~1plots/get/security").isMissingNode()).isTrue();
    }

    @Test
    void nullableFieldsAreMarkedInSwagger() throws Exception {
        JsonNode schemas = apiDocs().at("/components/schemas");

        assertThat(schemas.at("/PlotResponse/properties/plantingDate/type").toString()).contains("null");
        assertThat(schemas.at("/CooperativeResponse/properties/sector/type").toString()).contains("null");
    }

    /** If this fails, a new or renamed endpoint needs documenting in docs/API.md. */
    @Test
    void everyPublicEndpointIsInTheWrittenContract() throws Exception {
        String contract = Files.readString(Path.of("..", "docs", "API.md"));
        JsonNode paths = apiDocs().get("paths");

        List<String> missing = new ArrayList<>();
        paths.fieldNames().forEachRemaining(path -> paths.get(path).fieldNames().forEachRemaining(method -> {
            String entry = "`" + method.toUpperCase() + " " + path.substring(API_PREFIX.length()) + "`";
            if (!contract.contains(entry)) {
                missing.add(entry);
            }
        }));

        assertThat(missing).isEmpty();
    }

    private static List<JsonNode> operations(JsonNode paths) {
        List<JsonNode> operations = new ArrayList<>();
        paths.forEach(path -> path.forEach(operations::add));
        assertThat(operations).isNotEmpty();
        return operations;
    }
}
