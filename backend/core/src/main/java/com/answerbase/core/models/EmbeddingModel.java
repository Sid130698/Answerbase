package com.answerbase.core.models;

import java.io.IOException;
import java.util.List;

public interface EmbeddingModel {
    String identity() throws IOException;

    List<float[]> embed(List<String> texts, boolean query) throws IOException;

    static String vector(float[] values) {
        if (values.length != 768)
            throw new IllegalArgumentException("Expected 768 embedding dimensions");
        StringBuilder result = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (!Float.isFinite(values[i])) throw new IllegalArgumentException("Invalid embedding");
            if (i > 0) result.append(',');
            result.append(values[i]);
        }
        return result.append(']').toString();
    }
}
