package com.answerbase.core.models;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
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
            var response = client.send(builder.build(), info -> new LimitedBody(8 * 1024 * 1024));
            if (response.statusCode() != 200)
                throw new IOException("Model service returned HTTP " + response.statusCode());
            return JSON.readTree(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Model request interrupted", e);
        }
    }

    /** Bound allocation while receiving the body, not after it has been buffered. */
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final int limit;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private Flow.Subscription subscription;

        LimitedBody(int limit) {
            this.limit = limit;
        }

        public CompletionStage<byte[]> getBody() {
            return body;
        }

        public void onSubscribe(Flow.Subscription value) {
            if (subscription != null) {
                value.cancel();
                return;
            }
            subscription = value;
            value.request(1);
        }

        public void onNext(List<ByteBuffer> buffers) {
            if (body.isDone()) return;
            for (var buffer : buffers) {
                if (buffer.remaining() > limit - bytes.size()) {
                    subscription.cancel();
                    body.completeExceptionally(new IOException("Model response exceeds limit"));
                    return;
                }
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }

        public void onError(Throwable error) {
            body.completeExceptionally(error);
        }

        public void onComplete() {
            body.complete(bytes.toByteArray());
        }
    }
}
