package com.answerbase.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfiguration {
    @Bean
    OpenApiCustomizer healthGroups() {
        return api -> {
            for (String group : List.of("readiness", "liveness")) {
                var content =
                        new Content()
                                .addMediaType(
                                        "application/json",
                                        new MediaType()
                                                .schema(
                                                        new ObjectSchema()
                                                                .addProperty(
                                                                        "status",
                                                                        new StringSchema())));
                api.getPaths()
                        .addPathItem(
                                "/actuator/health/" + group,
                                new PathItem()
                                        .get(
                                                new Operation()
                                                        .operationId(group)
                                                        .summary("Check API " + group)
                                                        .tags(List.of("Health"))
                                                        .responses(
                                                                new ApiResponses()
                                                                        .addApiResponse(
                                                                                "200",
                                                                                new ApiResponse()
                                                                                        .description(
                                                                                                "Healthy")
                                                                                        .content(
                                                                                                content))
                                                                        .addApiResponse(
                                                                                "503",
                                                                                new ApiResponse()
                                                                                        .description(
                                                                                                "Unhealthy")
                                                                                        .content(
                                                                                                content)))));
            }
        };
    }

    @Bean
    OpenAPI answerbaseOpenApi() {
        return new OpenAPI()
                .components(
                        new io.swagger.v3.oas.models.Components()
                                .addSecuritySchemes(
                                        "bearerAuth",
                                        new io.swagger.v3.oas.models.security.SecurityScheme()
                                                .type(
                                                        io.swagger.v3.oas.models.security
                                                                .SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")))
                .info(
                        new Info()
                                .title("Answerbase API")
                                .version("0.1.0")
                                .description(
                                        "Organization and workspace access, document ingestion and lifecycle, cited answers, feedback, administration, and staff support delegation. "
                                                + "Registration and login are provided directly by Supabase Auth. "
                                                + "Health status does not imply authorization to access application data."))
                .servers(List.of(new Server().url("/")));
    }
}
