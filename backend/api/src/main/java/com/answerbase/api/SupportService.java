package com.answerbase.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class SupportService {
    record Staff(UUID userId, String role) {}

    record Session(
            UUID id,
            UUID organizationId,
            String organizationName,
            String reason,
            Instant expiresAt) {}

    record Organization(UUID id, String name) {}

    record User(UUID id, String organizationRole) {}

    record Workspace(UUID id, String name) {}

    record Member(UUID workspaceId, UUID userId, String role) {}

    record Snapshot(
            Session session,
            List<User> users,
            List<Workspace> workspaces,
            List<Member> memberships) {}

    private final JdbcTemplate jdbc;
    private final WorkspaceService workspaces;
    private final WorkspaceMembershipService memberships;

    SupportService(
            JdbcTemplate jdbc,
            WorkspaceService workspaces,
            WorkspaceMembershipService memberships) {
        this.jdbc = jdbc;
        this.workspaces = workspaces;
        this.memberships = memberships;
    }

    Staff requireStaff(UUID actor) {
        var rows =
                jdbc.query(
                        "SELECT role FROM answerbase.staff_access WHERE user_id=? AND active",
                        (rs, row) -> new Staff(actor, rs.getString("role")),
                        actor);
        if (rows.isEmpty())
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Answerbase staff access is required");
        return rows.getFirst();
    }

    void audit(
            UUID actor,
            UUID session,
            UUID organization,
            UUID target,
            String action,
            String detail) {
        jdbc.update(
                "INSERT INTO answerbase.support_audit (actor_id,session_id,organization_id,target_user_id,action,detail) VALUES (?,?,?,?,?,?)",
                actor,
                session,
                organization,
                target,
                action,
                detail);
    }

    @Transactional
    public List<Organization> organizations(UUID actor, String query) {
        requireStaff(actor);
        if (query.length() > 100)
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Search is limited to 100 characters");
        var result =
                jdbc.query(
                        "SELECT id,name FROM answerbase.organizations WHERE strpos(lower(name),lower(?))>0 OR id::text=? ORDER BY name,id LIMIT 100",
                        (rs, row) ->
                                new Organization(
                                        rs.getObject("id", UUID.class), rs.getString("name")),
                        query,
                        query);
        audit(actor, null, null, null, "organization.search", "Search: " + query);
        return result;
    }

    @Transactional
    public Session start(UUID actor, UUID organization, String rawReason) {
        requireStaff(actor);
        String reason = rawReason == null ? "" : rawReason.strip();
        if (reason.length() < 5
                || reason.length() > 500
                || reason.codePoints().anyMatch(Character::isISOControl))
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Enter a ticket reference or support reason (5–500 characters)");
        if (jdbc.queryForList("SELECT id FROM answerbase.organizations WHERE id=?", organization)
                .isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found");
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO answerbase.support_sessions (id,actor_id,organization_id,reason) VALUES (?,?,?,?)",
                id,
                actor,
                organization,
                reason);
        audit(actor, id, organization, null, "session.start", reason);
        return requireSession(actor, id);
    }

    Session requireSession(UUID actor, UUID id) {
        requireStaff(actor);
        var rows =
                jdbc.query(
                        """
                SELECT s.id,s.organization_id,o.name,s.reason,s.expires_at
                FROM answerbase.support_sessions s JOIN answerbase.organizations o ON o.id=s.organization_id
                WHERE s.id=? AND s.actor_id=? AND s.closed_at IS NULL AND s.expires_at>now()
                FOR UPDATE OF s
                """,
                        (rs, row) ->
                                new Session(
                                        rs.getObject("id", UUID.class),
                                        rs.getObject("organization_id", UUID.class),
                                        rs.getString("name"),
                                        rs.getString("reason"),
                                        rs.getTimestamp("expires_at").toInstant()),
                        id,
                        actor);
        if (rows.isEmpty())
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Support session expired, ended, or unavailable. Start a new session.");
        return rows.getFirst();
    }

    User requireTarget(Session session, UUID target) {
        var rows =
                jdbc.query(
                        "SELECT id,organization_role FROM answerbase.users WHERE id=? AND organization_id=?",
                        (rs, row) ->
                                new User(
                                        rs.getObject("id", UUID.class),
                                        rs.getString("organization_role")),
                        target,
                        session.organizationId());
        if (rows.isEmpty())
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "User not found in this organization");
        return rows.getFirst();
    }

    @Transactional
    public Snapshot snapshot(UUID actor, UUID id) {
        var session = requireSession(actor, id);
        var users =
                jdbc.query(
                        "SELECT id,organization_role FROM answerbase.users WHERE organization_id=? ORDER BY id",
                        (rs, row) ->
                                new User(
                                        rs.getObject("id", UUID.class),
                                        rs.getString("organization_role")),
                        session.organizationId());
        var spaces =
                jdbc.query(
                        "SELECT id,name FROM answerbase.workspaces WHERE organization_id=? ORDER BY name,id",
                        (rs, row) ->
                                new Workspace(rs.getObject("id", UUID.class), rs.getString("name")),
                        session.organizationId());
        var members =
                jdbc.query(
                        "SELECT workspace_id,user_id,role FROM answerbase.workspace_members WHERE organization_id=? ORDER BY workspace_id,user_id",
                        (rs, row) ->
                                new Member(
                                        rs.getObject("workspace_id", UUID.class),
                                        rs.getObject("user_id", UUID.class),
                                        rs.getString("role")),
                        session.organizationId());
        audit(
                actor,
                id,
                session.organizationId(),
                null,
                "organization.inspect",
                "Organization users, workspaces and membership metadata");
        return new Snapshot(session, users, spaces, members);
    }

    @Transactional
    public List<WorkspaceService.Workspace> asUser(UUID actor, UUID id, UUID target) {
        var session = requireSession(actor, id);
        requireTarget(session, target);
        var result = workspaces.list(target);
        audit(
                actor,
                id,
                session.organizationId(),
                target,
                "user.inspect",
                "Exact workspace access as selected user");
        return result;
    }

    @Transactional
    public WorkspaceService.Creation createAsUser(
            UUID actor, UUID id, UUID target, UUID key, String name) {
        var session = requireSession(actor, id);
        requireTarget(session, target);
        var result = workspaces.create(target, key, name);
        audit(
                actor,
                id,
                session.organizationId(),
                target,
                "workspace.create_as_user",
                result.workspace().id() + "; created=" + result.created());
        return result;
    }

    @Transactional
    public void changeMembership(
            UUID actor,
            UUID id,
            UUID workspace,
            UUID target,
            WorkspaceMembershipService.Role role) {
        var session = requireSession(actor, id);
        requireTarget(session, target);
        jdbc.queryForList(
                "SELECT id FROM answerbase.workspaces WHERE id=? AND organization_id=? FOR UPDATE",
                workspace,
                session.organizationId());
        var before =
                jdbc.queryForList(
                        "SELECT role FROM answerbase.workspace_members WHERE organization_id=? AND workspace_id=? AND user_id=?",
                        session.organizationId(),
                        workspace,
                        target);
        memberships.changeInOrganization(session.organizationId(), workspace, target, role);
        audit(
                actor,
                id,
                session.organizationId(),
                target,
                "membership.change",
                "workspace="
                        + workspace
                        + "; before="
                        + before
                        + "; after="
                        + (role == null ? "no access" : role.name()));
    }

    @Transactional
    public void close(UUID actor, UUID id) {
        requireStaff(actor);
        var rows =
                jdbc.queryForList(
                        "SELECT organization_id FROM answerbase.support_sessions WHERE id=? AND actor_id=? FOR UPDATE",
                        id,
                        actor);
        if (rows.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Support session not found");
        int changed =
                jdbc.update(
                        "UPDATE answerbase.support_sessions SET closed_at=now() WHERE id=? AND closed_at IS NULL",
                        id);
        if (changed > 0)
            audit(
                    actor,
                    id,
                    (UUID) rows.getFirst().get("organization_id"),
                    null,
                    "session.end",
                    "Ended by staff member");
    }

    @Transactional
    public List<Map<String, Object>> history(UUID actor) {
        var staff = requireStaff(actor);
        var result =
                jdbc.queryForList(
                        "SELECT id,actor_id,session_id,organization_id,target_user_id,action,detail,occurred_at FROM answerbase.support_audit WHERE (? OR actor_id=?) ORDER BY id DESC LIMIT 100",
                        staff.role().equals("platform_admin"),
                        actor);
        audit(
                actor,
                null,
                null,
                null,
                "audit.read",
                staff.role().equals("platform_admin")
                        ? "Latest platform audit events"
                        : "Latest own audit events");
        return result;
    }
}
