package com.rinda.backend.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(title = "Rinda API", version = "v1",
                description = "Maize crop-insurance backend. Payouts are simulated. See docs/API.md."),
        security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {

    private static final String PROBLEM = "Problem";

    /** Lists the error format on every operation. Each endpoint's codes are in docs/API.md. */
    @Bean
    OpenApiCustomizer errorResponses() {
        return api -> {
            api.getComponents().addSchemas(PROBLEM, problemSchema());
            ApiResponse error = new ApiResponse()
                    .description("Error. Branch on `code`; see docs/API.md for each endpoint's codes.")
                    .content(new Content().addMediaType("application/problem+json", new MediaType()
                            .schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM))));
            api.getPaths().values().forEach(path -> path.readOperations()
                    .forEach(operation -> operation.getResponses().setDefault(error)));
        };
    }

    private static Schema<?> problemSchema() {
        return new ObjectSchema()
                .addProperty("type", new StringSchema())
                .addProperty("title", new StringSchema())
                .addProperty("status", new IntegerSchema())
                .addProperty("detail", new StringSchema().description("Plain-language message"))
                .addProperty("instance", new StringSchema())
                .addProperty("code", new StringSchema().description("Stable value that clients branch on"))
                .addProperty("fields", new ObjectSchema()
                        .description("Only with VALIDATION_FAILED: one message per field name")
                        .additionalProperties(new StringSchema()));
    }
}
