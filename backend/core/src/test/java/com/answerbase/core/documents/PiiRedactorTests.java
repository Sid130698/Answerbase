package com.answerbase.core.documents;

import static org.assertj.core.api.Assertions.*;

import com.answerbase.core.models.*;
import org.junit.jupiter.api.Test;

class PiiRedactorTests {
    @Test
    void minimizesPromptIdentifiersAndRestoresExactCitationLocally() {
        var redactor = new PiiRedactor();
        String original =
                "Contact alice@example.test or +1 (415) 555-1234. SSN 123-45-6789. Refunds within 30 days.";
        String masked = redactor.redact(original);
        assertThat(masked)
                .doesNotContain("alice@example.test", "415", "123-45-6789")
                .contains("30 days");
        assertThat(redactor.count()).isEqualTo(3);
        String json = JsonHttp.JSON.writeValueAsString(java.util.Map.of("quote", masked));
        assertThat(JsonHttp.JSON.readTree(redactor.restoreJson(json)).path("quote").asText())
                .isEqualTo(original);
        assertThat(redactor.redact("alice@example.test")).isNotEqualTo("alice@example.test");
        assertThat(redactor.count()).isEqualTo(3);
    }
}
