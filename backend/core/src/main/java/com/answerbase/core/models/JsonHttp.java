package com.answerbase.core.models;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public final class JsonHttp {
    public static final JsonMapper JSON = JsonMapper.builder().build();
    private final HttpClient client =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public JsonNode post(String url, Object body, String secret, int timeout) throws IOException {
        return request("POST", url, body, secret, timeout);
    }

    public JsonNode get(String url, int timeout) throws IOException {
        return request("GET", url, null, "", timeout);
    }

    private JsonNode request(String method, String url, Object body, String secret, int timeout)
            throws IOException {
        var builder =
                HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(timeout))
                        .header("Content-Type", "application/json");
        if (!secret.isBlank()) builder.header("Authorization", "Bearer " + secret);
        builder.method(
                method,
                body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        try {
            var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200)
                throw new IOException("Model service returned HTTP " + response.statusCode());
            if (response.body().length > 8 * 1024 * 1024)
                throw new IOException("Model response exceeds limit");
            return JSON.readTree(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Model request interrupted", e);
        }
    }
}
