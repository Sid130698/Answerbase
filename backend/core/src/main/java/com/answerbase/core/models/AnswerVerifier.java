package com.answerbase.core.models;

import java.util.*;

/** Structural grounding check; semantic correctness is measured separately in evaluations. */
public final class AnswerVerifier {
    public record Citation(String passageId, String quote) {}

    public record Claim(String text, List<Citation> citations) {}

    public record Answer(boolean abstained, List<Claim> claims) {}

    public Answer verify(String json, Map<String, String> evidence) {
        try {
            var answer = JsonHttp.JSON.readValue(json, Answer.class);
            if (answer.claims() == null || answer.claims().size() > 8)
                throw new IllegalArgumentException("Invalid claims");
            if (answer.abstained()) {
                if (!answer.claims().isEmpty())
                    throw new IllegalArgumentException("Abstention cannot include claims");
                return answer;
            }
            if (answer.claims().isEmpty())
                throw new IllegalArgumentException("Answer needs claims");
            for (var claim : answer.claims()) {
                if (claim.text() == null
                        || claim.text().isBlank()
                        || claim.text().length() > 2000
                        || claim.citations() == null
                        || claim.citations().isEmpty()
                        || claim.citations().size() > 6)
                    throw new IllegalArgumentException("Every claim needs citations");
                for (var citation : claim.citations()) {
                    String passage = evidence.get(citation.passageId());
                    if (passage == null
                            || citation.quote() == null
                            || citation.quote().strip().length() < 8
                            || !normalize(passage).contains(normalize(citation.quote())))
                        throw new IllegalArgumentException(
                                "Citation does not match retrieved evidence");
                }
            }
            return answer;
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to verify generated citations");
        }
    }

    private String normalize(String text) {
        return text.replaceAll("\\s+", " ").strip();
    }
}
