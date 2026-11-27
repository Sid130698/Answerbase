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
        var response = exchange(builder.build(), timeout, 8 * 1024 * 1024);
        if (response.statusCode() != 200)
            throw new IOException("Model service returned HTTP " + response.statusCode());
        return JSON.readTree(response.body());
    }

    /** Bounds total response bytes and time, including a stalled response body. */
    public HttpResponse<byte[]> exchange(HttpRequest request, int timeout, int maxBytes)
            throws IOException {
        var pending = client.sendAsync(request, info -> new LimitedBody(maxBytes));
        try {
            return pending.get(timeout, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            pending.cancel(true);
            throw new IOException("HTTP response deadline exceeded", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IOException io) throw io;
            throw new IOException("HTTP request failed", e.getCause());
        } catch (InterruptedException e) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            throw new IOException("HTTP request interrupted", e);
        }
    }

    /** NDJSON/SSE lines delivered as they arrive, under the same total byte/deadline limits. */
    public void stream(
            String url,
            Object requestBody,
            String secret,
            int timeout,
            java.util.function.Consumer<String> line)
            throws IOException {
        var builder =
                HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(timeout))
                        .header("Content-Type", "application/json")
                        .POST(
                                HttpRequest.BodyPublishers.ofString(
                                        JSON.writeValueAsString(requestBody)));
        if (!secret.isBlank()) builder.header("Authorization", "Bearer " + secret);
        var pending =
                client.sendAsync(
                        builder.build(),
                        info -> new LineBody(info.statusCode() == 200 ? line : ignored -> {}));
        try {
            var response = pending.get(timeout, TimeUnit.SECONDS);
            if (response.statusCode() != 200)
                throw new IOException("Model service returned HTTP " + response.statusCode());
        } catch (TimeoutException e) {
            pending.cancel(true);
            throw new IOException("Model response deadline exceeded", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new IOException("Model stream failed", e.getCause());
        } catch (InterruptedException e) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            throw new IOException("Model stream interrupted", e);
        }
    }

    private static final class LineBody implements HttpResponse.BodySubscriber<Void> {
        private final java.util.function.Consumer<String> consumer;
        private final ByteArrayOutputStream line = new ByteArrayOutputStream();
        private final CompletableFuture<Void> body = new CompletableFuture<>();
        private Flow.Subscription subscription;
        private int received;

        LineBody(java.util.function.Consumer<String> consumer) {
            this.consumer = consumer;
        }

        public CompletionStage<Void> getBody() {
            return body;
        }

        public void onSubscribe(Flow.Subscription value) {
            subscription = value;
            value.request(1);
        }

        private void emit() {
            if (line.size() > 0)
                consumer.accept(
                        line.toString(java.nio.charset.StandardCharsets.UTF_8).stripTrailing());
            line.reset();
        }

        public void onNext(List<ByteBuffer> buffers) {
            if (body.isDone()) return;
            try {
                for (var buffer : buffers)
                    while (buffer.hasRemaining()) {
                        if (++received > 8 * 1024 * 1024)
                            throw new IOException("Model response exceeds limit");
                        byte next = buffer.get();
                        if (next == '\n') emit();
                        else line.write(next);
                    }
                subscription.request(1);
            } catch (Exception e) {
                subscription.cancel();
                body.completeExceptionally(e);
            }
        }

        public void onError(Throwable error) {
            body.completeExceptionally(error);
        }

        public void onComplete() {
            try {
                emit();
                body.complete(null);
            } catch (Exception e) {
                body.completeExceptionally(e);
            }
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
