package com.answerbase.core.models;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Hosted gte-small vectors are zero-padded; cosine distance is unchanged. */
public final class SupabaseEmbedding implements EmbeddingModel {
    private final String endpoint, secret;
    private final JsonHttp http = new JsonHttp();

    public SupabaseEmbedding(String endpoint, String secret) {
        if (!"https".equals(URI.create(endpoint).getScheme()) || secret.isBlank())
            throw new IllegalArgumentException(
                    "Hosted embeddings require HTTPS and a server secret");
        this.endpoint = endpoint;
        this.secret = secret;
    }

    public String identity() {
        return "supabase:gte-small:mean-normalized:384-pad768:v1";
    }

    public List<float[]> embed(List<String> texts, boolean query) throws IOException {
        var result = new ArrayList<float[]>();
        for (String text : texts) {
            var response = http.post(endpoint, Map.of("text", text), secret, 60);
            result.add(parse(response));
        }
        return result;
    }

    static float[] parse(tools.jackson.databind.JsonNode response) throws IOException {
        var vector = response.path("embedding");
        if (vector.size() != 384) throw new IOException("Expected 384 embedding dimensions");
        float[] values = new float[768];
        double norm = 0;
        for (int i = 0; i < 384; i++) {
            if (!vector.get(i).isNumber()) throw new IOException("Non-numeric embedding");
            values[i] = (float) vector.get(i).asDouble();
            if (!Float.isFinite(values[i])) throw new IOException("Non-finite embedding");
            norm += values[i] * values[i];
        }
        if (norm == 0) throw new IOException("Empty embedding");
        return values;
    }
}
