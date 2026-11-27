package com.answerbase.core.documents;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;

/** Server-only credentials. The bucket must be private with no client object policies. */
public final class SupabaseDocumentStorage implements DocumentStorage {
    private final String base, bucket, secret;
    private final com.answerbase.core.models.JsonHttp http =
            new com.answerbase.core.models.JsonHttp();

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
        return requestEndpoint(method, "object/" + bucket + "/" + key, data, type);
    }

    private byte[] requestEndpoint(String method, String path, byte[] data, String type)
            throws IOException {
        var request =
                HttpRequest.newBuilder(URI.create(base + "/storage/v1/" + path))
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
        var response = http.exchange(request, 30, MAX_BYTES);
        if (method.equals("DELETE") && response.statusCode() == 404) return new byte[0];
        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new IOException("Private storage request failed: HTTP " + response.statusCode());
        return response.body();
    }

    /** Flat paginated inventory. Unknown timestamps/keys never qualify for deletion. */
    public java.util.List<String> staleKeys(Duration age) throws IOException {
        var keys = new java.util.ArrayList<String>();
        var cursors = new java.util.HashSet<String>();
        var cutoff = java.time.Instant.now().minus(age);
        String cursor = "";
        for (int page = 0; page < 100; page++) {
            var body = new java.util.LinkedHashMap<String, Object>();
            body.put("prefix", "organizations/");
            body.put("limit", 1000);
            body.put("with_delimiter", false);
            if (!cursor.isEmpty()) body.put("cursor", cursor);
            var response =
                    com.answerbase.core.models.JsonHttp.JSON.readTree(
                            requestEndpoint(
                                    "POST",
                                    "object/list-v2/" + bucket,
                                    com.answerbase.core.models.JsonHttp.JSON.writeValueAsBytes(
                                            body),
                                    "application/json"));
            if (!response.path("objects").isArray())
                throw new IOException("Invalid storage inventory response");
            keys.addAll(staleInventoryKeys(response, cutoff));
            if (!response.path("hasNext").asBoolean()) return keys;
            cursor = response.path("nextCursor").asText("");
            if (cursor.isEmpty() || !cursors.add(cursor))
                throw new IOException("Invalid storage inventory cursor");
        }
        throw new IOException("Storage inventory exceeds scan limit");
    }

    static java.util.List<String> staleInventoryKeys(
            tools.jackson.databind.JsonNode response, java.time.Instant cutoff) {
        var keys = new java.util.ArrayList<String>();
        for (var object : response.path("objects")) {
            String key = object.path("key").asText(object.path("name").asText(""));
            if (!key.startsWith("organizations/")
                    || !key.matches("[a-zA-Z0-9/_-]+")
                    || object.path("is_delete_marker").asBoolean()) continue;
            try {
                if (java.time.Instant.parse(object.path("updated_at").asText()).isBefore(cutoff))
                    keys.add(key);
            } catch (java.time.format.DateTimeParseException invalid) {
                /* never delete uncertain-age objects */
            }
        }
        return keys;
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
