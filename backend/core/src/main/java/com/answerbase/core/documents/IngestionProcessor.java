package com.answerbase.core.documents;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** Lease-based queue. Publication of chunks and processed status is atomic and fenced. */
public final class IngestionProcessor {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final DocumentStorage storage;

    public IngestionProcessor(JdbcTemplate jdbc, TransactionTemplate tx, DocumentStorage storage) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.storage = storage;
    }

    public boolean processOne() {
        UUID lease = UUID.randomUUID();
        var job =
                tx.execute(
                        status -> {
                            var rows =
                                    jdbc.queryForList(
                                            """
                SELECT j.version_id FROM answerbase.ingestion_jobs j
                WHERE (j.state='queued' AND j.available_at<=now()) OR (j.state='processing' AND j.lease_until<now())
                ORDER BY j.available_at FOR UPDATE SKIP LOCKED LIMIT 1
                """);
                            if (rows.isEmpty()) return null;
                            UUID id = (UUID) rows.getFirst().get("version_id");
                            jdbc.update(
                                    "UPDATE answerbase.ingestion_jobs SET state='processing',attempts=attempts+1,lease_token=?,lease_until=now()+interval '5 minutes' WHERE version_id=?",
                                    lease,
                                    id);
                            jdbc.update(
                                    "UPDATE answerbase.document_versions SET status='processing',error=NULL WHERE id=?",
                                    id);
                            return jdbc.queryForMap(
                                    "SELECT v.*,j.attempts FROM answerbase.document_versions v JOIN answerbase.ingestion_jobs j ON j.version_id=v.id WHERE v.id=?",
                                    id);
                        });
        if (job == null) return false;
        UUID id = (UUID) job.get("id");
        try {
            if (((Number) job.get("attempts")).intValue() > 3)
                throw new java.io.IOException(
                        "Processing repeatedly interrupted. Retry to start a new attempt.");
            byte[] bytes = storage.get((String) job.get("storage_key"));
            String checksum =
                    HexFormat.of()
                            .formatHex(
                                    java.security.MessageDigest.getInstance("SHA-256")
                                            .digest(bytes));
            if (!checksum.equals(job.get("checksum")))
                throw new java.io.IOException(
                        "Stored file checksum mismatch. Upload a new version.");
            var chunks = new TextExtractor().extract(bytes, (String) job.get("media_type"));
            tx.executeWithoutResult(
                    status -> {
                        if (!owns(id, lease)) return;
                        jdbc.update(
                                "DELETE FROM answerbase.document_chunks WHERE version_id=?", id);
                        for (var chunk : chunks)
                            jdbc.update(
                                    "INSERT INTO answerbase.document_chunks (organization_id,workspace_id,version_id,ordinal,page_number,start_line,end_line,content) VALUES (?,?,?,?,?,?,?,?)",
                                    job.get("organization_id"),
                                    job.get("workspace_id"),
                                    id,
                                    chunk.ordinal(),
                                    chunk.pageNumber(),
                                    chunk.startLine(),
                                    chunk.endLine(),
                                    chunk.content());
                        jdbc.update(
                                "UPDATE answerbase.document_versions SET status='processed',error=NULL WHERE id=?",
                                id);
                        jdbc.update(
                                "UPDATE answerbase.ingestion_jobs SET state='done',lease_token=NULL,lease_until=NULL WHERE version_id=?",
                                id);
                    });
        } catch (Exception failure) {
            tx.executeWithoutResult(
                    status -> {
                        if (!owns(id, lease)) return;
                        boolean retry = ((Number) job.get("attempts")).intValue() < 3;
                        String error =
                                failure instanceof java.io.IOException
                                        ? failure.getMessage()
                                        : "Processing failed. Retry or upload another version.";
                        if (error == null)
                            error = "Unable to read this file. Upload another version.";
                        jdbc.update(
                                "UPDATE answerbase.document_versions SET status=?,error=? WHERE id=?",
                                retry ? "queued" : "failed",
                                error.substring(0, Math.min(500, error.length())),
                                id);
                        jdbc.update(
                                "UPDATE answerbase.ingestion_jobs SET state=?,available_at=now()+interval '30 seconds',lease_token=NULL,lease_until=NULL WHERE version_id=?",
                                retry ? "queued" : "failed",
                                id);
                    });
        }
        return true;
    }

    private boolean owns(UUID id, UUID lease) {
        return !jdbc.queryForList(
                        "SELECT version_id FROM answerbase.ingestion_jobs WHERE version_id=? AND lease_token=? AND state='processing' FOR UPDATE",
                        id,
                        lease)
                .isEmpty();
    }
}
