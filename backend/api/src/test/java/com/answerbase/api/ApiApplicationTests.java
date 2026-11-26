package com.answerbase.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

@org.springframework.test.context.ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiApplicationTests {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;

    @Test
    void migrationHistoryRemainsDiscoverableAfterApplicationSchemaExists() {
        // PostgreSQL's default "$user" search path now resolves to answerbase.
        // A new Flyway instance must still find the original public history.
        var restarted = Flyway.configure().configuration(flyway.getConfiguration()).load();
        assertThat(restarted.migrate().migrationsExecuted).isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM public.flyway_schema_history WHERE success",
                                Integer.class))
                .isPositive();
    }

    @Test
    void migrationsInstallVectorAndApplicationSchema() {
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM pg_extension WHERE extname = 'vector'",
                                Integer.class))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM information_schema.schemata WHERE schema_name = 'answerbase'",
                                Integer.class))
                .isEqualTo(1);
    }

    @Test
    void readinessIsPublicButApplicationAndManagementRoutesAreClosed() throws Exception {
        assertThat(get("/actuator/health/readiness").statusCode()).isEqualTo(200);
        assertThat(get("/api/v1/workspaces").statusCode()).isEqualTo(401);
        assertThat(get("/actuator/env").statusCode()).isEqualTo(401);
    }

    @Test
    void openApiDescribesImplementedHealthEndpointsAndSwaggerLoads() throws Exception {
        var specification = get("/v3/api-docs");
        assertThat(specification.statusCode()).isEqualTo(200);
        var json =
                tools.jackson.databind.json.JsonMapper.builder()
                        .build()
                        .readTree(specification.body());
        assertThat(json.path("info").path("title").asText()).isEqualTo("Answerbase API");
        assertThat(json.path("paths").has("/actuator/health")).isTrue();
        for (String group : java.util.List.of("readiness", "liveness")) {
            String path = "/actuator/health/" + group;
            assertThat(json.path("paths").path(path).has("get")).isTrue();
            assertThat(get(path).statusCode()).isEqualTo(200);
        }
        assertThat(json.path("paths").has("/api/v1/workspaces")).isTrue();
        assertThat(get("/swagger-ui/index.html").statusCode()).isEqualTo(200);
        assertThat(get("/v3/api-docs/swagger-config").statusCode()).isEqualTo(200);
    }

    private HttpResponse<String> get(String path) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var request =
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                            .GET()
                            .build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
}
