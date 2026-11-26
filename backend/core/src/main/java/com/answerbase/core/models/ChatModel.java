package com.answerbase.core.models;

import java.io.IOException;
import java.util.*;

/** Ollama locally or an OpenAI-compatible hosted endpoint (including Groq). */
public final class ChatModel implements GenerationModel {
    private final String provider, url, model, key;
    private final JsonHttp http = new JsonHttp();

    public ChatModel(String provider, String url, String model, String key) {
        this.provider = provider;
        this.url = url.replaceAll("/+$", "");
        this.model = model;
        this.key = key;
    }

    public ChatModel forModel(String selected) {
        return selected.equals("default") ? this : new ChatModel(provider, url, selected, key);
    }

    public Output generate(String system, String prompt) throws IOException {
        var messages =
                List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", prompt));
        if (provider.equals("ollama")) {
            var response =
                    http.post(
                            url + "/api/chat",
                            Map.of(
                                    "model",
                                    model,
                                    "messages",
                                    messages,
                                    "format",
                                    "json",
                                    "stream",
                                    false,
                                    "options",
                                    Map.of("temperature", 0, "num_predict", 1400, "num_ctx", 8192)),
                            "",
                            180);
            return new Output(
                    response.path("message").path("content").asText(),
                    "ollama:" + response.path("model").asText(),
                    response.path("prompt_eval_count").asLong(),
                    response.path("eval_count").asLong());
        }
        if (!provider.equals("groq") || key.isBlank() || !url.startsWith("https://"))
            throw new IOException("Hosted generation requires HTTPS and a server key");
        var response =
                http.post(
                        url + "/chat/completions",
                        Map.of(
                                "model",
                                model,
                                "messages",
                                messages,
                                "temperature",
                                0,
                                "max_completion_tokens",
                                1400,
                                "response_format",
                                Map.of("type", "json_object")),
                        key,
                        90);
        return new Output(
                response.path("choices").path(0).path("message").path("content").asText(),
                "groq:" + response.path("model").asText(),
                response.path("usage").path("prompt_tokens").asLong(),
                response.path("usage").path("completion_tokens").asLong());
    }
}
