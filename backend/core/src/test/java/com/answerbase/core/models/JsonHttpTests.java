package com.answerbase.core.models;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonHttpTests {
    @Test
    void handlesJsonAndRejectsHttpFailureAndOversizeChunkedResponse() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/ok",
                exchange -> {
                    assertEquals(
                            "Bearer test-secret",
                            exchange.getRequestHeaders().getFirst("Authorization"));
                    assertTrue(
                            new String(
                                            exchange.getRequestBody().readAllBytes(),
                                            StandardCharsets.UTF_8)
                                    .contains("question"));
                    var body = "{\"answer\":42}".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, body.length);
                    try (var out = exchange.getResponseBody()) {
                        out.write(body);
                    }
                });
        server.createContext(
                "/failure",
                exchange -> {
                    exchange.sendResponseHeaders(503, -1);
                    exchange.close();
                });
        server.createContext(
                "/large",
                exchange -> {
                    exchange.sendResponseHeaders(200, 0);
                    try (var out = exchange.getResponseBody()) {
                        byte[] block = new byte[65536];
                        for (int i = 0; i < 160; i++) out.write(block);
                    } catch (IOException expectedWhenClientCancels) {
                        /* bounded receiver cancels */
                    }
                });
        server.start();
        try {
            var client = new JsonHttp();
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            assertEquals(
                    42,
                    client.post(base + "/ok", Map.of("question", "test"), "test-secret", 5)
                            .get("answer")
                            .asInt());
            assertTrue(
                    assertThrows(IOException.class, () -> client.get(base + "/failure", 5))
                            .getMessage()
                            .contains("503"));
            assertThrows(IOException.class, () -> client.get(base + "/large", 5));
        } finally {
            server.stop(0);
        }
    }
}
