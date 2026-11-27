package com.answerbase.core.models;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/** Extracts complete claims only; raw model tokens never reach the client. */
public final class VerifiedClaimStream implements Consumer<String> {
    private static final Pattern PREFIX =
            Pattern.compile(
                    "^\\s*\\{\\s*\"abstained\"\\s*:\\s*false\\s*,\\s*\"claims\"\\s*:\\s*\\[");
    private final StringBuilder buffer = new StringBuilder();
    private final Map<String, String> evidence;
    private final UnaryOperator<String> restore;
    private final Consumer<AnswerVerifier.Claim> emit;
    private int cursor, depth, start = -1, count;
    private boolean prefix, quoted, escaped, stopped;

    public VerifiedClaimStream(
            Map<String, String> evidence,
            UnaryOperator<String> restore,
            Consumer<AnswerVerifier.Claim> emit) {
        this.evidence = Map.copyOf(evidence);
        this.restore = restore;
        this.emit = emit;
    }

    public void accept(String token) {
        if (stopped) return;
        buffer.append(token);
        if (buffer.length() > 128 * 1024) {
            stopped = true;
            return;
        }
        if (!prefix) {
            var match = PREFIX.matcher(buffer);
            if (!match.find()) return;
            prefix = true;
            cursor = match.end();
        }
        while (cursor < buffer.length()) {
            char next = buffer.charAt(cursor++);
            if (quoted) {
                if (escaped) escaped = false;
                else if (next == '\\') escaped = true;
                else if (next == '"') quoted = false;
                continue;
            }
            if (next == '"') {
                quoted = true;
                continue;
            }
            if (next == '{') {
                if (depth++ == 0) start = cursor - 1;
            } else if (next == '}' && --depth == 0 && start >= 0) {
                AnswerVerifier.Claim claim;
                try {
                    var answer =
                            new AnswerVerifier()
                                    .verify(
                                            restore.apply(
                                                    "{\"abstained\":false,\"claims\":["
                                                            + buffer.substring(start, cursor)
                                                            + "]}"),
                                            evidence);
                    claim = answer.claims().getFirst();
                    if (++count > 8) {
                        stopped = true;
                        return;
                    }
                } catch (IllegalArgumentException invalid) {
                    stopped = true;
                    return;
                }
                // Do not swallow delivery/access failures as malformed model output.
                emit.accept(claim);
                start = -1;
            } else if (next == ']' && depth == 0) {
                stopped = true;
                return;
            }
        }
    }
}
