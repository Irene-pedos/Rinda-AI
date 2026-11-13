package com.rinda.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.test.context.TestPropertySource;

/**
 * Writes the OpenAPI spec to docs/openapi.json for the Android app and dashboard. Runs only when asked:
 * scripts\mvn.ps1 test "-Dtest=OpenApiExportTest" "-Drinda.openapi.export=true"
 * docs/API.md stays the written contract; this file is generated from the code.
 */
@TestPropertySource(properties = "springdoc.api-docs.enabled=true")
@EnabledIfSystemProperty(named = "rinda.openapi.export", matches = "true")
class OpenApiExportTest extends ApiTestBase {

    @Test
    void exportSpec() throws Exception {
        String body = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Object json = mapper.readValue(body, Object.class);
        Files.writeString(Path.of("..", "docs", "openapi.json"),
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json) + "\n");
    }
}
