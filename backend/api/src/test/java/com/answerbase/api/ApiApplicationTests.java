package com.answerbase.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiApplicationTests {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;

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
        assertThat(get("/api/v1/workspaces").statusCode()).isEqualTo(403);
        assertThat(get("/actuator/env").statusCode()).isEqualTo(403);
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
