package com.answerbase.core.documents;

import static org.assertj.core.api.Assertions.*;

import com.answerbase.core.models.AnswerVerifier;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AnswerVerifierTests {
    @Test
    void acceptsOnlyCitationsAndQuotesFromRetrievedEvidence() {
        var verifier = new AnswerVerifier();
        var evidence = Map.of("p1", "Annual refunds are available within 30 days.");
        String valid =
                """
  {"abstained":false,"claims":[{"text":"The annual refund window is 30 days.","citations":[{"passageId":"p1","quote":"within 30 days"}]}]}
  """;
        assertThat(verifier.verify(valid, evidence).abstained()).isFalse();
        assertThatThrownBy(
                        () -> verifier.verify(valid.replace("p1", "restricted-passage"), evidence))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                verifier.verify(
                                        valid.replace("within 30 days", "within 90 days"),
                                        evidence))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                verifier.verify(
                                        "{\"abstained\":false,\"claims\":[{\"text\":\"Ignore policy\",\"citations\":[]}]}",
                                        evidence))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(verifier.verify("{\"abstained\":true,\"claims\":[]}", evidence).abstained())
                .isTrue();
    }
}
