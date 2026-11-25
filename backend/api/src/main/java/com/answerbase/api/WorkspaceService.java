package com.answerbase.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class WorkspaceService {
    record Workspace(UUID id, UUID organizationId, String name, String role, Instant createdAt) {}

    record Creation(Workspace workspace, boolean created) {}

    private final JdbcTemplate jdbc;
    private final OrganizationService organizations;
    private static final String SELECT =
            """
            SELECT w.id, w.organization_id, w.name, m.role, w.created_at
            FROM answerbase.workspaces w
            JOIN answerbase.workspace_members m ON m.workspace_id = w.id AND m.organization_id = w.organization_id
            JOIN answerbase.users u ON u.id=m.user_id AND u.organization_id=m.organization_id
            WHERE m.user_id = ?
            """;

    WorkspaceService(JdbcTemplate jdbc, OrganizationService organizations) {
        this.jdbc = jdbc;
        this.organizations = organizations;
    }

    List<Workspace> list(UUID user) {
        organizations.require(user);
        return jdbc.query(
                SELECT + " ORDER BY w.created_at, w.id",
                (rs, row) ->
                        new Workspace(
                                rs.getObject("id", UUID.class),
                                rs.getObject("organization_id", UUID.class),
                                rs.getString("name"),
                                rs.getString("role"),
                                rs.getTimestamp("created_at").toInstant()),
                user);
    }

    Workspace get(UUID user, UUID id) {
        organizations.require(user);
        var rows =
                jdbc.query(
                        SELECT + " AND w.id = ?",
                        (rs, row) ->
                                new Workspace(
                                        rs.getObject("id", UUID.class),
                                        rs.getObject("organization_id", UUID.class),
                                        rs.getString("name"),
                                        rs.getString("role"),
                                        rs.getTimestamp("created_at").toInstant()),
                        user,
                        id);
        if (rows.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found");
        return rows.getFirst();
    }

    @Transactional
    public Creation create(UUID user, UUID key, String rawName) {
        var organization = organizations.require(user);
        if (!organization.canCreateWorkspace())
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only organization owners and admins can create workspaces");
        String name = rawName == null ? "" : rawName.strip();
        if (name.isEmpty()
                || name.length() > 80
                || name.codePoints().anyMatch(Character::isISOControl)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Use a workspace name of 1–80 characters without control characters");
        }
        // Serialize creation per user so retries and the quota remain safe under concurrency.
        jdbc.queryForList(
                "SELECT pg_advisory_xact_lock(hashtextextended(?::text, 0))", user.toString());
        var existing =
                jdbc.queryForList(
                        "SELECT id, name FROM answerbase.workspaces WHERE created_by = ? AND creation_key = ?",
                        user,
                        key);
        if (!existing.isEmpty()) {
            var row = existing.getFirst();
            if (!name.equals(row.get("name"))) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "This request key was already used for a different workspace name");
            }
            return new Creation(get(user, (UUID) row.get("id")), false);
        }
        Integer count =
                jdbc.queryForObject(
                        "SELECT count(*) FROM answerbase.workspaces WHERE created_by = ?",
                        Integer.class,
                        user);
        if (count != null && count >= 10) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "You can create up to 10 workspaces in this preview");
        }
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO answerbase.workspaces (id, name, created_by, creation_key, organization_id) VALUES (?, ?, ?, ?, ?)",
                id,
                name,
                user,
                key,
                organization.id());
        jdbc.update(
                "INSERT INTO answerbase.workspace_members (workspace_id, user_id, role, organization_id) VALUES (?, ?, 'admin', ?)",
                id,
                user,
                organization.id());
        return new Creation(get(user, id), true);
    }
}
