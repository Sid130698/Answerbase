package com.answerbase.core.models;

import java.util.*;
import java.util.regex.Pattern;

/** Pattern-based prompt minimization, not comprehensive PII detection. Originals remain private. */
public final class PiiRedactor {
    private static final Pattern IDENTIFIERS =
            Pattern.compile(
                    "(?i)(?<![\\w])(?:[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9.-]+\\.[a-z]{2,}|\\b\\d{3}-\\d{2}-\\d{4}\\b|(?<![a-f0-9-])\\+?\\d[\\d ()-]{7,}\\d(?![a-f0-9-]))");
    private final String prefix = "[PII_" + UUID.randomUUID().toString().replace("-", "") + "_";
    private final Map<String, String> replacements = new LinkedHashMap<>();

    public String redact(String text) {
        var matcher = IDENTIFIERS.matcher(text);
        return matcher.replaceAll(
                match -> {
                    String value = match.group();
                    String token =
                            replacements.entrySet().stream()
                                    .filter(e -> e.getValue().equals(value))
                                    .map(Map.Entry::getKey)
                                    .findFirst()
                                    .orElse(null);
                    if (token == null) {
                        token = prefix + replacements.size() + "]";
                        replacements.put(token, value);
                    }
                    return java.util.regex.Matcher.quoteReplacement(token);
                });
    }

    public String restoreJson(String json) {
        String result = json;
        for (var entry : replacements.entrySet()) {
            String escaped = JsonHttp.JSON.writeValueAsString(entry.getValue());
            result = result.replace(entry.getKey(), escaped.substring(1, escaped.length() - 1));
        }
        return result;
    }

    public int count() {
        return replacements.size();
    }
}
