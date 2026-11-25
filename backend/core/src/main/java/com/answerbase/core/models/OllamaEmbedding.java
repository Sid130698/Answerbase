package com.answerbase.core.models;

import java.io.IOException;
import java.util.*;

public final class OllamaEmbedding implements EmbeddingModel {
    private final String base, model;
    private final JsonHttp http = new JsonHttp();
    private volatile String identity;
    private volatile long resolved;

    public OllamaEmbedding(String base, String model) {
        this.base = base.replaceAll("/+$", "");
        this.model = model;
    }

    public synchronized String identity() throws IOException {
        if (identity != null && System.currentTimeMillis() - resolved < 60000) return identity;
        var models = http.get(base + "/api/tags", 10).path("models");
        for (var candidate : models)
            if (candidate.path("name").asText().equals(model)) {
                identity = "ollama:" + model + "@" + candidate.path("digest").asText();
                resolved = System.currentTimeMillis();
                return identity;
            }
        throw new IOException("Embedding model is not installed. Pull " + model + " in Ollama.");
    }

    public List<float[]> embed(List<String> texts, boolean query) throws IOException {
        if (texts.isEmpty()) return List.of();
        var response =
                http.post(
                        base + "/api/embed",
                        Map.of(
                                "model",
                                model,
                                "input",
                                texts.stream()
                                        .map(
                                                t ->
                                                        (query
                                                                        ? "search_query: "
                                                                        : "search_document: ")
                                                                + t)
                                        .toList(),
                                "truncate",
                                false),
                        "",
                        120);
        var vectors = response.path("embeddings");
        if (vectors.size() != texts.size())
            throw new IOException("Embedding response count mismatch");
        var output = new ArrayList<float[]>();
        for (var vector : vectors) {
            if (vector.size() != 768)
                throw new IOException("Embedding model must return 768 dimensions");
            float[] values = new float[768];
            for (int i = 0; i < 768; i++) {
                values[i] = (float) vector.get(i).asDouble();
                if (!Float.isFinite(values[i])) throw new IOException("Non-finite embedding");
            }
            output.add(values);
        }
        return output;
    }
}
