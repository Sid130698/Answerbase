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
        return generate(system, prompt, ignored -> {});
    }

    public Output generate(String system, String prompt, java.util.function.Consumer<String> tokens)
            throws IOException {
        var messages =
                List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", prompt));
        var body = new LinkedHashMap<String, Object>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("stream", true);
        boolean local = provider.equals("ollama");
        if (local) {
            body.put("format", "json");
            body.put("options", Map.of("temperature", 0, "num_predict", 1400, "num_ctx", 8192));
        } else {
            if (!provider.equals("groq") || key.isBlank() || !url.startsWith("https://"))
                throw new IOException("Hosted generation requires HTTPS and a server key");
            body.put("temperature", 0);
            body.put("max_completion_tokens", 1400);
            body.put("response_format", Map.of("type", "json_object"));
            body.put("stream_options", Map.of("include_usage", true));
        }
        var text = new StringBuilder();
        long[] usage = new long[2];
        boolean[] finished = {false};
        String[] actualModel = {model};
        http.stream(
                url + (local ? "/api/chat" : "/chat/completions"),
                body,
                local ? "" : key,
                local ? 180 : 90,
                line -> {
                    if (line.isBlank() || (!local && !line.startsWith("data:"))) return;
                    String payload = local ? line : line.substring(5).strip();
                    if (payload.equals("[DONE]")) {
                        finished[0] = true;
                        return;
                    }
                    var part = JsonHttp.JSON.readTree(payload);
                    if (part.has("error"))
                        throw new IllegalArgumentException("Model returned a streaming error");
                    if (part.has("model")) actualModel[0] = part.path("model").asText();
                    String delta =
                            local
                                    ? part.path("message").path("content").asText("")
                                    : part.path("choices")
                                            .path(0)
                                            .path("delta")
                                            .path("content")
                                            .asText("");
                    if (!delta.isEmpty()) {
                        text.append(delta);
                        tokens.accept(delta);
                    }
                    if (local && part.path("done").asBoolean()) {
                        finished[0] = true;
                        usage[0] = part.path("prompt_eval_count").asLong();
                        usage[1] = part.path("eval_count").asLong();
                    } else if (!local && part.hasNonNull("usage")) {
                        usage[0] = part.path("usage").path("prompt_tokens").asLong();
                        usage[1] = part.path("usage").path("completion_tokens").asLong();
                    }
                });
        if (!finished[0]) throw new IOException("Model stream ended before completion");
        return new Output(text.toString(), provider + ":" + actualModel[0], usage[0], usage[1]);
    }
}
