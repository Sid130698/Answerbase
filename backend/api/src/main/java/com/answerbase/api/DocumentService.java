package com.answerbase.api;

import com.answerbase.core.documents.DocumentStorage;
import java.io.IOException;
import java.security.*;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.server.ResponseStatusException;

@Service
class DocumentService {
    record Version(
            UUID id,
            UUID documentId,
            int versionNumber,
            String filename,
            String mediaType,
            int byteSize,
            String status,
            String error,
            Instant createdAt) {}

    record Document(UUID id, String title, Version latest, UUID processedVersionId) {}

    record Original(String filename, String mediaType, byte[] bytes) {}

    record Chunk(
            int ordinal,
            Integer pageNumber,
            Integer startLine,
            Integer endLine,
            String content,
            String sectionPath,
            Integer startOffset,
            Integer endOffset) {}

    private final JdbcTemplate jdbc;
    private final WorkspaceService workspaces;
    private final DocumentStorage storage;

    DocumentService(JdbcTemplate jdbc, WorkspaceService workspaces, DocumentStorage storage) {
        this.jdbc = jdbc;
        this.workspaces = workspaces;
        this.storage = storage;
    }

    private WorkspaceService.Workspace access(UUID user, UUID workspace, boolean write) {
        var w = workspaces.get(user, workspace);
        if (write && !Set.of("admin", "editor").contains(w.role()))
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Editor or admin access is required to upload or retry documents");
        return w;
    }

    private Version map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Version(
                rs.getObject("id", UUID.class),
                rs.getObject("document_id", UUID.class),
                rs.getInt("version_number"),
                rs.getString("filename"),
                rs.getString("media_type"),
                rs.getInt("byte_size"),
                rs.getString("status"),
                rs.getString("error"),
                rs.getTimestamp("created_at").toInstant());
    }

    List<Document> list(UUID user, UUID workspace) {
        var w = access(user, workspace, false);
        return jdbc.query(
                """
            SELECT d.id AS document,d.title,v.*,
              (SELECT p.id FROM answerbase.document_versions p WHERE p.document_id=d.id AND p.status='processed' ORDER BY p.version_number DESC LIMIT 1) AS processed
            FROM answerbase.documents d
            JOIN LATERAL (SELECT * FROM answerbase.document_versions WHERE document_id=d.id ORDER BY version_number DESC LIMIT 1) v ON true
            WHERE d.organization_id=? AND d.workspace_id=? ORDER BY d.created_at DESC,d.id
            """,
                (rs, n) ->
                        new Document(
                                rs.getObject("document", UUID.class),
                                rs.getString("title"),
                                map(rs),
                                rs.getObject("processed", UUID.class)),
                w.organizationId(),
                workspace);
    }

    record Duplicate(
            UUID documentId,
            String title,
            UUID similarDocumentId,
            String similarTitle,
            String kind,
            double similarity) {}

    List<Duplicate> duplicates(UUID user, UUID workspace) {
        var w = access(user, workspace, true);
        var rows =
                jdbc.queryForList(
                        "SELECT d.id,d.title,v.checksum,v.extracted_text FROM answerbase.documents d JOIN LATERAL (SELECT checksum,extracted_text FROM answerbase.document_versions WHERE document_id=d.id ORDER BY version_number DESC LIMIT 1) v ON true WHERE d.organization_id=? AND d.workspace_id=? ORDER BY d.id LIMIT 2000",
                        w.organizationId(),
                        workspace);
        var tokens =
                rows.stream()
                        .map(
                                r ->
                                        new HashSet<>(
                                                Arrays.asList(
                                                        Objects.toString(
                                                                        r.get("extracted_text"), "")
                                                                .toLowerCase(Locale.ROOT)
                                                                .split("\\W+"))))
                        .toList();
        var result = new ArrayList<Duplicate>();
        for (int i = 0; i < rows.size(); i++)
            for (int j = i + 1; j < rows.size(); j++) {
                var a = rows.get(i);
                var b = rows.get(j);
                boolean exact = a.get("checksum").equals(b.get("checksum"));
                double score = 0;
                if (exact) score = 1;
                else if (tokens.get(i).size() >= 10 && tokens.get(j).size() >= 10) {
                    var intersection = new HashSet<>(tokens.get(i));
                    intersection.retainAll(tokens.get(j));
                    score =
                            (double) intersection.size()
                                    / (tokens.get(i).size()
                                            + tokens.get(j).size()
                                            - intersection.size());
                }
                if (score >= 0.85)
                    result.add(
                            new Duplicate(
                                    (UUID) a.get("id"),
                                    (String) a.get("title"),
                                    (UUID) b.get("id"),
                                    (String) b.get("title"),
                                    exact ? "identical" : "similar vocabulary",
                                    score));
                if (result.size() >= 100) return result;
            }
        return result;
    }

    List<Version> versions(UUID user, UUID workspace, UUID document) {
        var w = access(user, workspace, false);
        if (jdbc.queryForList(
                        "SELECT id FROM answerbase.documents WHERE id=? AND organization_id=? AND workspace_id=?",
                        document,
                        w.organizationId(),
                        workspace)
                .isEmpty()) throw missing();
        return jdbc.query(
                "SELECT * FROM answerbase.document_versions WHERE document_id=? AND organization_id=? AND workspace_id=? ORDER BY version_number DESC",
                (rs, n) -> map(rs),
                document,
                w.organizationId(),
                workspace);
    }

    private Map<String, Object> version(
            UUID user, UUID workspace, UUID document, UUID version, boolean write) {
        var w = access(user, workspace, write);
        var rows =
                jdbc.queryForList(
                        "SELECT * FROM answerbase.document_versions WHERE id=? AND document_id=? AND organization_id=? AND workspace_id=?",
                        version,
                        document,
                        w.organizationId(),
                        workspace);
        if (rows.isEmpty()) throw missing();
        return rows.getFirst();
    }

    private ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Document or version not found");
    }

    @Transactional
    public Version upload(
            UUID user,
            UUID workspace,
            UUID document,
            UUID requestKey,
            String rawFilename,
            byte[] bytes) {
        var w = access(user, workspace, true);
        jdbc.queryForList("SELECT id FROM answerbase.workspaces WHERE id=? FOR UPDATE", workspace);
        access(user, workspace, true);
        String filename = rawFilename == null ? "" : rawFilename.strip();
        if (filename.isEmpty()
                || filename.length() > 200
                || filename.contains("/")
                || filename.contains("\\")
                || filename.codePoints().anyMatch(Character::isISOControl))
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Use a filename of 1–200 characters without paths or control characters");
        if (bytes.length == 0 || bytes.length > DocumentStorage.MAX_BYTES)
            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE, "Upload a non-empty file up to 5 MB");
        String lower = filename.toLowerCase(Locale.ROOT);
        String media =
                lower.endsWith(".pdf")
                        ? "application/pdf"
                        : lower.endsWith(".txt")
                                ? "text/plain"
                                : lower.endsWith(".md")
                                        ? "text/markdown"
                                        : lower.endsWith(".docx")
                                                ? "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                                : (lower.endsWith(".html")
                                                                || lower.endsWith(".htm"))
                                                        ? "text/html"
                                                        : null;
        if (media == null)
            throw new ResponseStatusException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Upload a PDF, DOCX, HTML, TXT, or Markdown file");
        if (media.equals("application/pdf")
                && (bytes.length < 5
                        || !new String(bytes, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)
                                .equals("%PDF-")))
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "This file does not have a PDF signature");
        String checksum;
        try {
            checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        var previous =
                jdbc.queryForList(
                        "SELECT * FROM answerbase.document_versions WHERE workspace_id=? AND created_by=? AND request_key=?",
                        workspace,
                        user,
                        requestKey);
        if (!previous.isEmpty()) {
            var v = previous.getFirst();
            boolean sameTarget =
                    document == null
                            ? ((Number) v.get("version_number")).intValue() == 1
                            : document.equals(v.get("document_id"));
            if (!sameTarget
                    || !checksum.equals(v.get("checksum"))
                    || !filename.equals(v.get("filename")))
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "This upload request key was used for different content");
            return jdbc.queryForObject(
                    "SELECT * FROM answerbase.document_versions WHERE id=?",
                    (rs, n) -> map(rs),
                    v.get("id"));
        }
        if (document != null) {
            var same =
                    jdbc.query(
                            "SELECT * FROM answerbase.document_versions WHERE document_id=? AND organization_id=? AND workspace_id=? ORDER BY version_number DESC LIMIT 1",
                            (rs, n) -> map(rs),
                            document,
                            w.organizationId(),
                            workspace);
            if (!same.isEmpty()
                    && !jdbc.queryForList(
                                    "SELECT id FROM answerbase.document_versions WHERE id=? AND checksum=?",
                                    same.getFirst().id(),
                                    checksum)
                            .isEmpty()) return same.getFirst();
        }
        if (jdbc.queryForObject(
                        "SELECT count(*) FROM answerbase.document_versions WHERE workspace_id=?",
                        Integer.class,
                        workspace)
                >= 200)
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This workspace has reached its 200-version preview limit");
        long used =
                jdbc.queryForObject(
                        "SELECT COALESCE(sum(byte_size),0) FROM answerbase.document_versions WHERE workspace_id=?",
                        Long.class,
                        workspace);
        if (used + bytes.length > 100L * 1024 * 1024)
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This workspace has reached its 100 MB preview storage limit");
        int number = 1;
        if (document == null) {
            document = UUID.randomUUID();
            jdbc.update(
                    "INSERT INTO answerbase.documents(id,organization_id,workspace_id,title,created_by) VALUES (?,?,?,?,?)",
                    document,
                    w.organizationId(),
                    workspace,
                    filename,
                    user);
        } else {
            if (jdbc.queryForList(
                            "SELECT id FROM answerbase.documents WHERE id=? AND organization_id=? AND workspace_id=?",
                            document,
                            w.organizationId(),
                            workspace)
                    .isEmpty()) throw missing();
            number =
                    jdbc.queryForObject(
                            "SELECT COALESCE(max(version_number),0)+1 FROM answerbase.document_versions WHERE document_id=?",
                            Integer.class,
                            document);
        }
        UUID id = UUID.randomUUID();
        String key =
                "organizations/"
                        + w.organizationId()
                        + "/workspaces/"
                        + workspace
                        + "/documents/"
                        + document
                        + "/versions/"
                        + id
                        + "/original";
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED)
                            try {
                                storage.delete(key);
                            } catch (IOException e) {
                                org.slf4j.LoggerFactory.getLogger(DocumentService.class)
                                        .warn("Upload rollback left an orphan object: {}", key);
                            }
                    }
                });
        try {
            storage.put(key, bytes, media);
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "File storage is unavailable. Retry the same upload.");
        }
        jdbc.update(
                "INSERT INTO answerbase.document_versions(id,organization_id,workspace_id,document_id,version_number,filename,media_type,byte_size,checksum,storage_key,request_key,created_by) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                id,
                w.organizationId(),
                workspace,
                document,
                number,
                filename,
                media,
                bytes.length,
                checksum,
                key,
                requestKey,
                user);
        jdbc.update("INSERT INTO answerbase.ingestion_jobs(version_id) VALUES (?)", id);
        return jdbc.queryForObject(
                "SELECT * FROM answerbase.document_versions WHERE id=?", (rs, n) -> map(rs), id);
    }

    Original original(UUID user, UUID workspace, UUID document, UUID id) {
        var v = version(user, workspace, document, id, false);
        try {
            return new Original(
                    (String) v.get("filename"),
                    (String) v.get("media_type"),
                    storage.get((String) v.get("storage_key")));
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Original file is temporarily unavailable");
        }
    }

    List<Chunk> chunks(UUID user, UUID workspace, UUID document, UUID id, int offset) {
        var v = version(user, workspace, document, id, false);
        if (!v.get("status").equals("processed"))
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "This version has not finished processing");
        if (offset < 0)
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Offset must be non-negative");
        return jdbc.query(
                "SELECT * FROM answerbase.document_chunks WHERE version_id=? AND organization_id=? AND workspace_id=? ORDER BY ordinal LIMIT 50 OFFSET ?",
                (rs, n) ->
                        new Chunk(
                                rs.getInt("ordinal"),
                                rs.getObject("page_number", Integer.class),
                                rs.getObject("start_line", Integer.class),
                                rs.getObject("end_line", Integer.class),
                                rs.getString("content"),
                                rs.getString("section_path"),
                                rs.getObject("start_offset", Integer.class),
                                rs.getObject("end_offset", Integer.class)),
                id,
                v.get("organization_id"),
                workspace,
                offset);
    }

    @Transactional
    public void retry(UUID user, UUID workspace, UUID document, UUID id) {
        version(user, workspace, document, id, true);
        int changed =
                jdbc.update(
                        "UPDATE answerbase.ingestion_jobs SET state='queued',attempts=0,available_at=now(),lease_token=NULL,lease_until=NULL WHERE version_id=? AND state='failed'",
                        id);
        if (changed == 0)
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Only failed processing can be retried");
        jdbc.update(
                "UPDATE answerbase.document_versions SET status='queued',error=NULL WHERE id=?",
                id);
    }
}
