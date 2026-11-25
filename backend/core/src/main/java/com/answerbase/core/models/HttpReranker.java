package com.answerbase.core.models;

import java.io.IOException;
import java.util.*;

/** Private, self-hosted cross-encoder service; no external account required. */
public final class HttpReranker implements Reranker {
    private final String url;
    private final JsonHttp http = new JsonHttp();

    public HttpReranker(String url) {
        this.url = url;
    }

    public List<Double> scores(String query, List<String> passages) throws IOException {
        var result =
                http.post(url + "/rerank", Map.of("query", query, "passages", passages), "", 60)
                        .path("scores");
        if (result.size() != passages.size())
            throw new IOException("Reranker response count mismatch");
        var scores = new ArrayList<Double>();
        for (var score : result) {
            double value = score.asDouble();
            if (!Double.isFinite(value)) throw new IOException("Invalid reranker score");
            scores.add(value);
        }
        return scores;
    }
}
