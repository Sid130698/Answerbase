package com.answerbase.core.documents;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** Authorization belongs to the API; worker retention uses trusted organization/workspace IDs. */
public final class DocumentLifecycle {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public DocumentLifecycle(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    public boolean delete(UUID organization, UUID workspace, UUID document) {
        return delete(organization, workspace, document, false);
    }

    private boolean delete(UUID organization, UUID workspace, UUID document, boolean expiredOnly) {
        return Boolean.TRUE.equals(
                tx.execute(
                        status -> {
                            jdbc.queryForList(
                                    "SELECT id FROM answerbase.workspaces WHERE id=? AND organization_id=? FOR UPDATE",
                                    workspace,
                                    organization);
                            if (jdbc.queryForList(
                                            "SELECT id FROM answerbase.documents WHERE id=? AND organization_id=? AND workspace_id=?",
                                            document,
                                            organization,
                                            workspace)
                                    .isEmpty()) return false;
                            if (expiredOnly
                                    && jdbc.queryForList(
                                                    "SELECT 1 FROM answerbase.workspace_retention r WHERE r.workspace_id=? AND r.document_days>0 AND NOT EXISTS(SELECT 1 FROM answerbase.document_versions v WHERE v.document_id=? AND v.created_at>now()-r.document_days*interval '1 day')",
                                                    workspace,
                                                    document)
                                            .isEmpty()) return false;
                            // Lock/remove ingestion before indexing, matching the ingestion
                            // publication order.
                            jdbc.update(
                                    "DELETE FROM answerbase.ingestion_jobs WHERE version_id IN(SELECT id FROM answerbase.document_versions WHERE document_id=?)",
                                    document);
                            jdbc.update(
                                    "DELETE FROM answerbase.index_jobs WHERE version_id IN(SELECT id FROM answerbase.document_versions WHERE document_id=?)",
                                    document);
                            for (var version :
                                    jdbc.queryForList(
                                            "SELECT storage_key FROM answerbase.document_versions WHERE document_id=?",
                                            document))
                                jdbc.update(
                                        "INSERT INTO answerbase.object_deletions(id,organization_id,workspace_id,storage_key) VALUES(?,?,?,?) ON CONFLICT(storage_key) DO NOTHING",
                                        UUID.randomUUID(),
                                        organization,
                                        workspace,
                                        version.get("storage_key"));
                            // Cancel in-flight workspace answers and erase every saved answer
                            // carrying this source.
                            // Publication takes the same workspace lock, so no deleted evidence can
                            // be republished.
                            jdbc.update(
                                    "DELETE FROM answerbase.questions q WHERE q.workspace_id=? AND (q.status='running' OR EXISTS(SELECT 1 FROM jsonb_array_elements(coalesce(q.evidence,'[]'::jsonb)) e WHERE e->>'documentId'=?))",
                                    workspace,
                                    document.toString());
                            jdbc.update(
                                    "DELETE FROM answerbase.document_chunks WHERE version_id IN(SELECT id FROM answerbase.document_versions WHERE document_id=?)",
                                    document);
                            jdbc.update(
                                    "DELETE FROM answerbase.document_versions WHERE document_id=?",
                                    document);
                            jdbc.update("DELETE FROM answerbase.documents WHERE id=?", document);
                            return true;
                        }));
    }

    public int retain() {
        int deleted = 0;
        var expired =
                jdbc.queryForList(
                        "SELECT d.id,d.organization_id,d.workspace_id FROM answerbase.documents d JOIN answerbase.workspace_retention r ON r.workspace_id=d.workspace_id WHERE r.document_days>0 AND NOT EXISTS(SELECT 1 FROM answerbase.document_versions v WHERE v.document_id=d.id AND v.created_at>now()-r.document_days*interval '1 day') ORDER BY d.created_at LIMIT 100");
        for (var row : expired)
            if (delete(
                    (UUID) row.get("organization_id"),
                    (UUID) row.get("workspace_id"),
                    (UUID) row.get("id"),
                    true)) deleted++;
        jdbc.update(
                "DELETE FROM answerbase.questions q USING answerbase.workspace_retention r WHERE q.workspace_id=r.workspace_id AND r.question_days>0 AND q.created_at<now()-r.question_days*interval '1 day'");
        return deleted;
    }
}
