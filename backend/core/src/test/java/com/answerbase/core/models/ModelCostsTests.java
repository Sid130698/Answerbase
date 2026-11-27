package com.answerbase.core.models;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ModelCostsTests {
    @Test
    void pricesActualModelAndLeavesUnknownModelsUnpriced() {
        var costs =
                new ModelCosts(
                        "{\"groq:model-a\":{\"inputPerMillion\":0.1,\"outputPerMillion\":0.2},\"groq:free\":{\"inputPerMillion\":0,\"outputPerMillion\":0}}");
        assertThat(costs.estimate("groq:model-a", 2000, 1000))
                .isCloseTo(0.0004, within(0.000000001));
        assertThat(costs.estimate("groq:free", 2000, 1000)).isZero();
        assertThat(costs.estimate("groq:other", 2000, 1000)).isNull();
        assertThat(new ModelCosts("").estimate("groq:model-a", 0, 0)).isNull();
    }

    @Test
    void rejectsInvalidRatesAndCounts() {
        for (String invalid :
                new String[] {
                    "[]",
                    "{\"m\":{}}",
                    "{\"m\":{\"inputPerMillion\":-1,\"outputPerMillion\":0}}",
                    "{\"m\":{\"inputPerMillion\":\"free\",\"outputPerMillion\":0}}"
                })
            assertThatThrownBy(() -> new ModelCosts(invalid))
                    .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ModelCosts("").estimate("m", -1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
