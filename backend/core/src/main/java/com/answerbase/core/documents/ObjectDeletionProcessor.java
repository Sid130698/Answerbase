package com.answerbase.core.documents;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** Durable outbox: metadata removal commits before idempotent private-object deletion. */
public final class ObjectDeletionProcessor {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final DocumentStorage storage;

    public ObjectDeletionProcessor(
            JdbcTemplate jdbc, TransactionTemplate tx, DocumentStorage storage) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.storage = storage;
    }

    public int reconcile() throws java.io.IOException {
        int queued = 0;
        for (String key : storage.staleKeys(java.time.Duration.ofDays(7))) {
            String[] parts = key.split("/");
            if (parts.length != 9
                    || !parts[0].equals("organizations")
                    || !parts[2].equals("workspaces")
                    || !parts[4].equals("documents")
                    || !parts[6].equals("versions")
                    || !(parts[8].equals("original")
                            || parts[8].matches("upload-[a-zA-Z0-9-]+\\.part"))) continue;
            UUID organization, workspace;
            try {
                organization = UUID.fromString(parts[1]);
                workspace = UUID.fromString(parts[3]);
                UUID.fromString(parts[5]);
                UUID.fromString(parts[7]);
            } catch (IllegalArgumentException invalid) {
                continue;
            }
            try {
                Integer inserted =
                        tx.execute(
                                s -> {
                                    // Upload holds this row lock while writing originals; skip busy
                                    // workspaces.
                                    jdbc.queryForList(
                                            "SELECT id FROM answerbase.workspaces WHERE id=? FOR UPDATE NOWAIT",
                                            workspace);
                                    if (!jdbc.queryForList(
                                                    "SELECT id FROM answerbase.document_versions WHERE storage_key=?",
                                                    key)
                                            .isEmpty()) return 0;
                                    return jdbc.update(
                                            "INSERT INTO answerbase.object_deletions(id,organization_id,workspace_id,storage_key) VALUES(?,?,?,?) ON CONFLICT(storage_key) DO NOTHING",
                                            UUID.randomUUID(),
                                            organization,
                                            workspace,
                                            key);
                                });
                queued += inserted == null ? 0 : inserted;
            } catch (org.springframework.dao.CannotAcquireLockException busy) {
                /* retry on next scan */
            }
        }
        return queued;
    }

    public boolean processOne() {
        UUID token = UUID.randomUUID();
        var job =
                tx.execute(
                        s -> {
                            var rows =
                                    jdbc.queryForList(
                                            "SELECT id,storage_key FROM answerbase.object_deletions WHERE available_at<=now() AND (lease_until IS NULL OR lease_until<now()) ORDER BY available_at FOR UPDATE SKIP LOCKED LIMIT 1");
                            if (rows.isEmpty()) return null;
                            var row = rows.getFirst();
                            jdbc.update(
                                    "UPDATE answerbase.object_deletions SET lease_token=?,lease_until=now()+interval '2 minutes',attempts=attempts+1 WHERE id=?",
                                    token,
                                    row.get("id"));
                            return row;
                        });
        if (job == null) return false;
        try {
            storage.delete((String) job.get("storage_key"));
            jdbc.update(
                    "DELETE FROM answerbase.object_deletions WHERE id=? AND lease_token=?",
                    job.get("id"),
                    token);
        } catch (Exception failure) {
            jdbc.update(
                    "UPDATE answerbase.object_deletions SET lease_token=NULL,lease_until=NULL,error='Private storage deletion failed; retry scheduled',available_at=now()+least(3600,30*attempts)*interval '1 second' WHERE id=? AND lease_token=?",
                    job.get("id"),
                    token);
        }
        return true;
    }
}
