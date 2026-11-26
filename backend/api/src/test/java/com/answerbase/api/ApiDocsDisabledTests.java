package com.answerbase.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@org.springframework.test.context.ActiveProfiles("test")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"springdoc.api-docs.enabled=false", "springdoc.swagger-ui.enabled=false"})
class ApiDocsDisabledTests {
    @LocalServerPort int port;

    @Test
    void disablingDocumentationAlsoClosesDocumentationRoutes() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            for (String path :
                    new String[] {"/v3/api-docs", "/swagger-ui/index.html", "/api/v1/workspaces"}) {
                var request =
                        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                                .GET()
                                .build();
                assertThat(client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode())
                        .isEqualTo(401);
            }
        }
    }
}
