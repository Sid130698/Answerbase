package com.answerbase.core.models;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public record WorkspacePolicy(
        String retrievalMode,
        int candidates,
        int topK,
        boolean rerank,
        String generationModel,
        int chunkChars,
        boolean contextLine,
        boolean redactPii,
        int dailyAttempts) {
    public static final WorkspacePolicy DEFAULT =
            new WorkspacePolicy("hybrid", 30, 6, false, "default", 1800, true, true, 100);

    public static WorkspacePolicy read(
            JdbcTemplate jdbc, UUID workspace, WorkspacePolicy fallback) {
        var rows =
                jdbc.query(
                        "SELECT * FROM answerbase.workspace_settings WHERE workspace_id=?",
                        (r, n) ->
                                new WorkspacePolicy(
                                        r.getString("retrieval_mode"),
                                        r.getInt("candidates"),
                                        r.getInt("top_k"),
                                        r.getBoolean("rerank"),
                                        r.getString("generation_model"),
                                        r.getInt("chunk_chars"),
                                        r.getBoolean("context_line"),
                                        r.getBoolean("redact_pii"),
                                        r.getInt("daily_attempts")),
                        workspace);
        return rows.isEmpty() ? fallback : rows.getFirst();
    }
}
