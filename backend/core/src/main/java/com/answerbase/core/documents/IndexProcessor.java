package com.answerbase.core.documents;

import com.answerbase.core.models.EmbeddingModel;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

public final class IndexProcessor {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final EmbeddingModel model;

    public IndexProcessor(JdbcTemplate jdbc, TransactionTemplate tx, EmbeddingModel model) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.model = model;
    }

    public boolean processOne() throws java.io.IOException {
        String identity = model.identity();
        UUID token = UUID.randomUUID();
        var job =
                tx.execute(
                        s -> {
                            var rows =
                                    jdbc.queryForList(
                                            "SELECT j.version_id,j.attempts FROM answerbase.index_jobs j JOIN answerbase.document_versions v ON v.id=j.version_id WHERE v.status='processed' AND ((j.state='queued' AND j.available_at<=now()) OR (j.state='processing' AND j.lease_until<now())) ORDER BY j.available_at FOR UPDATE OF j SKIP LOCKED LIMIT 1");
                            if (rows.isEmpty()) return null;
                            var row = rows.getFirst();
                            UUID id = (UUID) row.get("version_id");
                            jdbc.update(
                                    "UPDATE answerbase.index_jobs SET state='processing',attempts=attempts+1,lease_token=?,lease_until=now()+interval '10 minutes' WHERE version_id=?",
                                    token,
                                    id);
                            jdbc.update(
                                    "UPDATE answerbase.document_versions SET index_status='indexing' WHERE id=?",
                                    id);
                            return row;
                        });
        if (job == null) return false;
        UUID id = (UUID) job.get("version_id");
        try {
            if (((Number) job.get("attempts")).intValue() >= 3)
                throw new java.io.IOException("Embedding repeatedly interrupted. Retry indexing.");
            var rows =
                    jdbc.queryForList(
                            "SELECT ordinal,section_path,content FROM answerbase.document_chunks WHERE version_id=? ORDER BY ordinal",
                            id);
            UUID workspace =
                    jdbc.queryForObject(
                            "SELECT workspace_id FROM answerbase.document_versions WHERE id=?",
                            UUID.class,
                            id);
            var policy =
                    com.answerbase.core.models.WorkspacePolicy.read(
                            jdbc, workspace, com.answerbase.core.models.WorkspacePolicy.DEFAULT);
            var vectors = new ArrayList<float[]>();
            for (int start = 0; start < rows.size(); start += 8) {
                var batch = rows.subList(start, Math.min(start + 8, rows.size()));
                vectors.addAll(
                        model.embed(
                                batch.stream()
                                        .map(
                                                r ->
                                                        (policy.contextLine()
                                                                        ? r.get("section_path")
                                                                                + "\n"
                                                                        : "")
                                                                + r.get("content"))
                                        .toList(),
                                false));
            }
            if (!identity.equals(model.identity()))
                throw new java.io.IOException("Embedding model changed during indexing. Retry.");
            tx.executeWithoutResult(
                    s -> {
                        if (!owns(id, token)) return;
                        for (int i = 0; i < rows.size(); i++)
                            jdbc.update(
                                    "UPDATE answerbase.document_chunks SET embedding=?::vector,embedding_model=? WHERE version_id=? AND ordinal=?",
                                    EmbeddingModel.vector(vectors.get(i)),
                                    identity,
                                    id,
                                    rows.get(i).get("ordinal"));
                        jdbc.update(
                                "UPDATE answerbase.document_versions SET index_status='indexed',indexed_at=now() WHERE id=?",
                                id);
                        jdbc.update(
                                "UPDATE answerbase.index_jobs SET state='done',error=NULL,lease_token=NULL,lease_until=NULL WHERE version_id=?",
                                id);
                    });
        } catch (Exception e) {
            tx.executeWithoutResult(
                    s -> {
                        if (!owns(id, token)) return;
                        boolean retry = ((Number) job.get("attempts")).intValue() < 2;
                        jdbc.update(
                                "UPDATE answerbase.index_jobs SET state=?,error=?,available_at=now()+interval '30 seconds',lease_token=NULL,lease_until=NULL WHERE version_id=?",
                                retry ? "queued" : "failed",
                                "Embedding unavailable or invalid. Check local model service, then retry indexing.",
                                id);
                        jdbc.update(
                                "UPDATE answerbase.document_versions SET index_status=? WHERE id=?",
                                retry ? "pending" : "failed",
                                id);
                    });
        }
        return true;
    }

    private boolean owns(UUID id, UUID token) {
        return !jdbc.queryForList(
                        "SELECT version_id FROM answerbase.index_jobs WHERE version_id=? AND lease_token=? AND state='processing' FOR UPDATE",
                        id,
                        token)
                .isEmpty();
    }
}
