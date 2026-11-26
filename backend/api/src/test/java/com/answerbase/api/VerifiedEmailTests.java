package com.answerbase.api;

import static org.assertj.core.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

class VerifiedEmailTests {
    @Test
    void confirmedIdentityComesFromAuthServerNotEditableMetadata() throws Exception {
        var body =
                new AtomicReference<>(
                        "{\"id\":\"user\",\"email\":\"confirmed@example.test\",\"email_confirmed_at\":\"2026-01-01T00:00:00Z\"}");
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/auth/v1/user",
                exchange -> {
                    byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, bytes.length);
                    try (var output = exchange.getResponseBody()) {
                        output.write(bytes);
                    }
                });
        server.start();
        try {
            var provider =
                    new SupabaseVerifiedEmailProvider(
                            "http://127.0.0.1:" + server.getAddress().getPort() + "/auth/v1",
                            "",
                            "public-key");
            var jwt =
                    Jwt.withTokenValue("test-token")
                            .header("alg", "RS256")
                            .subject("user")
                            .claim("user_metadata", java.util.Map.of("email_verified", true))
                            .build();
            assertThat(provider.email(jwt)).isEqualTo("confirmed@example.test");
            body.set(
                    "{\"id\":\"user\",\"email\":\"unconfirmed@example.test\",\"email_confirmed_at\":null}");
            assertThatThrownBy(() -> provider.email(jwt))
                    .isInstanceOfSatisfying(
                            ResponseStatusException.class,
                            e -> assertThat(e.getStatusCode().value()).isEqualTo(403));
            body.set(
                    "{\"id\":\"someone-else\",\"email\":\"confirmed@example.test\",\"email_confirmed_at\":\"2026-01-01T00:00:00Z\"}");
            assertThatThrownBy(() -> provider.email(jwt))
                    .isInstanceOf(ResponseStatusException.class);
        } finally {
            server.stop(0);
        }
    }
}
