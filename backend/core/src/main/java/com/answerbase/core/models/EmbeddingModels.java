package com.answerbase.core.models;

public final class EmbeddingModels {
    private EmbeddingModels() {}

    public static EmbeddingModel create(
            String provider, String url, String model, String endpoint, String secret) {
        return switch (provider) {
            case "ollama" -> new OllamaEmbedding(url, model);
            case "supabase" -> new SupabaseEmbedding(endpoint, secret);
            default -> throw new IllegalArgumentException("Unknown embedding provider");
        };
    }
}
