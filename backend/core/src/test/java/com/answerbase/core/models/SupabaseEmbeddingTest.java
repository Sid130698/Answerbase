package com.answerbase.core.models;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.Test;

class SupabaseEmbeddingTest {
    @Test
    void preservesGeometryAndPadsWithoutInventingDimensions() {
        var vector = new ArrayList<Double>(Collections.nCopies(384, 0.0));
        vector.set(0, 0.6);
        vector.set(383, 0.8);
        var result =
                assertDoesNotThrow(
                        () ->
                                SupabaseEmbedding.parse(
                                        JsonHttp.JSON.valueToTree(Map.of("embedding", vector))));
        assertEquals(768, result.length);
        assertEquals(0.6f, result[0]);
        assertEquals(0.8f, result[383]);
        for (int i = 384; i < 768; i++) assertEquals(0f, result[i]);
    }

    @Test
    void rejectsMalformedAndEmptyVectors() {
        for (var vector :
                List.of(List.of(1), Collections.nCopies(384, 0), Collections.nCopies(384, "bad")))
            assertThrows(
                    IOException.class,
                    () ->
                            SupabaseEmbedding.parse(
                                    JsonHttp.JSON.valueToTree(Map.of("embedding", vector))));
    }

    @Test
    void refusesInsecureEndpointAndMissingSecret() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SupabaseEmbedding("http://example.com", "secret"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SupabaseEmbedding("https://example.com", ""));
    }
}
