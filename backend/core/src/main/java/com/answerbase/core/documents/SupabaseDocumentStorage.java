package com.answerbase.core.documents;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;

/** Server-only credentials. The bucket must be private with no client object policies. */
public final class SupabaseDocumentStorage implements DocumentStorage {
    private final String base, bucket, secret;
    private final HttpClient client =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public SupabaseDocumentStorage(String base, String bucket, String secret) {
        if (!base.startsWith("https://") || !bucket.matches("[a-z0-9-]+") || secret.isBlank())
            throw new IllegalArgumentException(
                    "HTTPS storage URL, bucket and server secret are required");
        this.base = base.replaceAll("/+$", "");
        this.bucket = bucket;
        this.secret = secret;
    }

    private byte[] request(String method, String key, byte[] data, String type) throws IOException {
        if (!key.matches("[a-zA-Z0-9/_-]+")) throw new IOException("Invalid storage key");
        var request =
                HttpRequest.newBuilder(
                                URI.create(base + "/storage/v1/object/" + bucket + "/" + key))
                        .timeout(Duration.ofSeconds(30))
                        .header("apikey", secret)
                        .header("Authorization", "Bearer " + secret)
                        .header("Content-Type", type)
                        .method(
                                method,
                                data == null
                                        ? HttpRequest.BodyPublishers.noBody()
                                        : HttpRequest.BodyPublishers.ofByteArray(data))
                        .build();
        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (var stream = response.body()) {
                if (response.statusCode() < 200 || response.statusCode() >= 300)
                    throw new IOException(
                            "Private storage request failed: HTTP " + response.statusCode());
                byte[] bytes = stream.readNBytes(MAX_BYTES + 1);
                if (bytes.length > MAX_BYTES) throw new IOException("Stored file exceeds limit");
                return bytes;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Storage interrupted", e);
        }
    }

    public void put(String key, byte[] bytes, String type) throws IOException {
        request("POST", key, bytes, type);
    }

    public byte[] get(String key) throws IOException {
        return request("GET", key, null, "application/octet-stream");
    }

    public void delete(String key) throws IOException {
        request("DELETE", key, null, "application/octet-stream");
    }
}
