package com.answerbase.core.models;

import java.util.HashMap;
import java.util.Map;

/** Operator-supplied USD rates; absent models remain unpriced, never silently free. */
public final class ModelCosts {
    private record Rate(double input, double output) {}

    private final Map<String, Rate> rates;

    public ModelCosts(String configuration) {
        var values = new HashMap<String, Rate>();
        if (configuration != null && !configuration.isBlank()) {
            var root = JsonHttp.JSON.readTree(configuration);
            if (!root.isObject())
                throw new IllegalArgumentException("Model prices must be an object");
            root.properties()
                    .forEach(
                            entry -> {
                                var value = entry.getValue();
                                var input = value.path("inputPerMillion");
                                var output = value.path("outputPerMillion");
                                if (entry.getKey().isBlank()
                                        || !input.isNumber()
                                        || !output.isNumber()
                                        || !Double.isFinite(input.asDouble())
                                        || !Double.isFinite(output.asDouble())
                                        || input.asDouble() < 0
                                        || output.asDouble() < 0)
                                    throw new IllegalArgumentException(
                                            "Model prices require nonnegative finite input/output rates");
                                values.put(
                                        entry.getKey(),
                                        new Rate(input.asDouble(), output.asDouble()));
                            });
        }
        rates = Map.copyOf(values);
    }

    public Double estimate(String model, long input, long output) {
        if (input < 0 || output < 0)
            throw new IllegalArgumentException("Token counts cannot be negative");
        var rate = rates.get(model);
        if (rate == null) return null;
        double cost = (input / 1_000_000.0) * rate.input + (output / 1_000_000.0) * rate.output;
        if (!Double.isFinite(cost))
            throw new IllegalArgumentException("Model cost exceeds numeric range");
        return cost;
    }
}
