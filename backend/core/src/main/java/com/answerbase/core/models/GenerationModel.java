package com.answerbase.core.models;

import java.io.IOException;

public interface GenerationModel {
    record Output(String json, String model, long inputTokens, long outputTokens) {}

    Output generate(String system, String prompt) throws IOException;

    default Output generate(
            String system, String prompt, java.util.function.Consumer<String> tokens)
            throws IOException {
        Output result = generate(system, prompt);
        tokens.accept(result.json());
        return result;
    }
}
