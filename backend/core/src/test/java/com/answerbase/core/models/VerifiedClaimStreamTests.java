package com.answerbase.core.models;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class VerifiedClaimStreamTests {
    @Test
    void emitsCompleteVerifiedClaimBeforeTheAnswerEnds() {
        var claims = new ArrayList<AnswerVerifier.Claim>();
        var parser =
                new VerifiedClaimStream(
                        Map.of("p", "A quoted {bracket} in a sentence."), s -> s, claims::add);
        String json =
                "{\"abstained\":false,\"claims\":[{\"text\":\"Use {brackets} and \\\"quotes\\\".\",\"citations\":[{\"passageId\":\"p\",\"quote\":\"A quoted {bracket}\"}]}";
        for (int i = 0; i < json.length() - 1; i++) parser.accept(json.substring(i, i + 1));
        assertTrue(claims.isEmpty());
        parser.accept("}");
        assertEquals(1, claims.size());
        parser.accept("]}");
        assertEquals(1, claims.size());
    }

    @Test
    void refusesAbstentionAndUnverifiedQuotes() {
        for (String json :
                List.of(
                        "{\"abstained\":true,\"claims\":[{\"text\":\"x\",\"citations\":[]}]}",
                        "{\"abstained\":false,\"claims\":[{\"text\":\"x\",\"citations\":[{\"passageId\":\"p\",\"quote\":\"invented quotation\"}]}]}")) {
            var claims = new ArrayList<AnswerVerifier.Claim>();
            new VerifiedClaimStream(Map.of("p", "Real supporting passage"), s -> s, claims::add)
                    .accept(json);
            assertTrue(claims.isEmpty());
        }
    }

    @Test
    void propagatesAccessAndDeliveryFailures() {
        var parser =
                new VerifiedClaimStream(
                        Map.of("p", "Real supporting passage"),
                        s -> s,
                        c -> {
                            throw new IllegalStateException("access revoked");
                        });
        assertThrows(
                IllegalStateException.class,
                () ->
                        parser.accept(
                                "{\"abstained\":false,\"claims\":[{\"text\":\"x\",\"citations\":[{\"passageId\":\"p\",\"quote\":\"Real supporting passage\"}]}]}"));
    }
}
