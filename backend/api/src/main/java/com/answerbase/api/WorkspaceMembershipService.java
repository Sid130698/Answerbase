package com.answerbase.api;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class WorkspaceMembershipService {
    enum Role {
        admin,
        editor,
        viewer
    }

    record Member(UUID userId, Role role) {}

    private final JdbcTemplate jdbc;
    private final WorkspaceService workspaces;

    WorkspaceMembershipService(JdbcTemplate jdbc, WorkspaceService workspaces) {
        this.jdbc = jdbc;
        this.workspaces = workspaces;
    }

    private WorkspaceService.Workspace requireAdmin(UUID caller, UUID workspace) {
        var access = workspaces.get(caller, workspace);
        if (!access.role().equals("admin"))
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Only workspace admins can manage membership");
        return access;
    }

    List<Member> list(UUID caller, UUID workspace) {
        var access = requireAdmin(caller, workspace);
        return jdbc.query(
                "SELECT user_id, role FROM answerbase.workspace_members WHERE organization_id=? AND workspace_id=? ORDER BY user_id",
                (rs, row) ->
                        new Member(
                                rs.getObject("user_id", UUID.class),
                                Role.valueOf(rs.getString("role"))),
                access.organizationId(),
                workspace);
    }

    @Transactional
    public void change(UUID caller, UUID workspace, UUID target, Role role) {
        var access = requireAdmin(caller, workspace);
        jdbc.queryForList("SELECT id FROM answerbase.workspaces WHERE id=? FOR UPDATE", workspace);
        // Re-check after locking, in case another admin changed the caller's membership.
        requireAdmin(caller, workspace);
        changeInOrganization(access.organizationId(), workspace, target, role);
    }

    // Trusted internal entry point: caller authorization belongs to the customer or staff service.
    // Always enforces organization scope and last-admin invariants.
    void changeInOrganization(UUID organization, UUID workspace, UUID target, Role role) {
        var found =
                jdbc.queryForList(
                        "SELECT id FROM answerbase.workspaces WHERE id=? AND organization_id=? FOR UPDATE",
                        workspace,
                        organization);
        if (found.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found");
        var users =
                jdbc.queryForList(
                        "SELECT id FROM answerbase.users WHERE id=? AND organization_id=?",
                        target,
                        organization);
        if (users.isEmpty())
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Organization member not found");
        var previous =
                jdbc.queryForList(
                        "SELECT role FROM answerbase.workspace_members WHERE workspace_id=? AND user_id=?",
                        workspace,
                        target);
        if (role != Role.admin
                && !previous.isEmpty()
                && previous.getFirst().get("role").equals("admin")) {
            Integer admins =
                    jdbc.queryForObject(
                            "SELECT count(*) FROM answerbase.workspace_members WHERE workspace_id=? AND role='admin'",
                            Integer.class,
                            workspace);
            if (admins != null && admins <= 1)
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "A workspace must retain at least one admin");
        }
        if (role == null) {
            jdbc.update(
                    "DELETE FROM answerbase.group_members WHERE workspace_id=? AND user_id=?",
                    workspace,
                    target);
            jdbc.update(
                    "DELETE FROM answerbase.workspace_members WHERE organization_id=? AND workspace_id=? AND user_id=?",
                    organization,
                    workspace,
                    target);
        } else {
            jdbc.update(
                    """
                    INSERT INTO answerbase.workspace_members (organization_id, workspace_id, user_id, role)
                    VALUES (?, ?, ?, ?) ON CONFLICT (workspace_id, user_id) DO UPDATE SET role=excluded.role
                    """,
                    organization,
                    workspace,
                    target,
                    role.name());
        }
    }
}
