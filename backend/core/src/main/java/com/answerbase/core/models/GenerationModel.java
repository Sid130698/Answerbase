package com.answerbase.core.models;

import java.io.IOException;

public interface GenerationModel {
    record Output(String json, String model, long inputTokens, long outputTokens) {}

    Output generate(String system, String prompt) throws IOException;
}
