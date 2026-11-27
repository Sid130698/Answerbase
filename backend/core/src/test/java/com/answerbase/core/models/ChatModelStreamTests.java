package com.answerbase.core.models;

import static org.assertj.core.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ChatModelStreamTests {
    @Test
    void providerFailureAfterSuccessfulHeadersRemainsRetryable() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/api/chat",
                exchange -> {
                    exchange.getRequestBody().readAllBytes();
                    byte[] body =
                            "{\"error\":\"provider unavailable\"}\n"
                                    .getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                    exchange.close();
                });
        server.start();
        try {
            var model =
                    new ChatModel(
                            "ollama",
                            "http://127.0.0.1:" + server.getAddress().getPort(),
                            "test",
                            "");
            assertThatThrownBy(() -> model.generate("system", "question"))
                    .isInstanceOf(java.io.IOException.class)
                    .hasRootCauseMessage("Model returned a streaming error");
        } finally {
            server.stop(0);
        }
    }
}
