package com.answerbase.core.models;

import java.io.IOException;
import java.util.List;

public interface Reranker {
    List<Double> scores(String query, List<String> passages) throws IOException;
}
