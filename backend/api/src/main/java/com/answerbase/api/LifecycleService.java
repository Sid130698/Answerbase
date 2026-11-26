package com.answerbase.api;

import com.answerbase.core.documents.DocumentLifecycle;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
class LifecycleService {
    record Retention(int documentDays, int questionDays) {}

    private final JdbcTemplate jdbc;
    private final WorkspaceService workspaces;
    private final DocumentService documents;
    private final DocumentLifecycle lifecycle;

    LifecycleService(
            JdbcTemplate jdbc,
            WorkspaceService workspaces,
            DocumentService documents,
            PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.workspaces = workspaces;
        this.documents = documents;
        this.lifecycle = new DocumentLifecycle(jdbc, new TransactionTemplate(manager));
    }

    @Transactional
    public void delete(UUID user, UUID workspace, UUID document) {
        jdbc.queryForList("SELECT id FROM answerbase.workspaces WHERE id=? FOR UPDATE", workspace);
        var access = workspaces.get(user, workspace);
        if (!Set.of("admin", "editor").contains(access.role()))
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Editor or admin access required");
        documents.requireDocument(user, workspace, document);
        lifecycle.delete(access.organizationId(), workspace, document);
    }

    private void admin(UUID user, UUID workspace) {
        if (!workspaces.get(user, workspace).role().equals("admin"))
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Workspace admin access required");
    }

    Retention retention(UUID user, UUID workspace) {
        admin(user, workspace);
        var rows =
                jdbc.query(
                        "SELECT document_days,question_days FROM answerbase.workspace_retention WHERE workspace_id=?",
                        (r, n) -> new Retention(r.getInt(1), r.getInt(2)),
                        workspace);
        return rows.isEmpty() ? new Retention(0, 0) : rows.getFirst();
    }

    @Transactional
    public void retention(UUID user, UUID workspace, Retention policy) {
        jdbc.queryForList("SELECT id FROM answerbase.workspaces WHERE id=? FOR UPDATE", workspace);
        admin(user, workspace);
        if (policy.documentDays() < 0
                || policy.documentDays() > 3650
                || policy.questionDays() < 0
                || policy.questionDays() > 3650)
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Retention must be 0 (disabled) or 1–3650 days");
        jdbc.update(
                "INSERT INTO answerbase.workspace_retention(workspace_id,document_days,question_days) VALUES(?,?,?) ON CONFLICT(workspace_id) DO UPDATE SET document_days=excluded.document_days,question_days=excluded.question_days",
                workspace,
                policy.documentDays(),
                policy.questionDays());
    }

    Object pending(UUID user, UUID workspace) {
        admin(user, workspace);
        return jdbc.queryForMap(
                "SELECT count(*) AS pending,count(*) FILTER(WHERE attempts>0 AND error IS NOT NULL) AS retrying FROM answerbase.object_deletions WHERE workspace_id=?",
                workspace);
    }
}
