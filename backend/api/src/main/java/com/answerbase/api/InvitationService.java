package com.answerbase.api;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class InvitationService {
    record Created(UUID id, String token) {}

    private final JdbcTemplate jdbc;
    private final OrganizationService organizations;
    private final WorkspaceService workspaces;
    private final WorkspaceMembershipService members;

    InvitationService(
            JdbcTemplate jdbc,
            OrganizationService organizations,
            WorkspaceService workspaces,
            WorkspaceMembershipService members) {
        this.jdbc = jdbc;
        this.organizations = organizations;
        this.workspaces = workspaces;
        this.members = members;
    }

    private WorkspaceService.Workspace admin(UUID user, UUID workspace) {
        var org = organizations.require(user);
        var ws = workspaces.get(user, workspace);
        if (!org.canCreateWorkspace() || !ws.role().equals("admin"))
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Organization owner/admin and workspace admin access required");
        return ws;
    }

    private static String email(String raw) {
        String value = raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
        if (value.length() > 320 || !value.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Provide a valid email address");
        return value;
    }

    private static String hash(String token) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Transactional
    public Created create(
            UUID user, UUID workspace, String raw, WorkspaceMembershipService.Role role) {
        var ws = admin(user, workspace);
        if (role == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a workspace role");
        jdbc.queryForList("SELECT id FROM answerbase.workspaces WHERE id=? FOR UPDATE", workspace);
        admin(user, workspace);
        if (jdbc.queryForObject(
                        "SELECT count(*) FROM answerbase.organization_invitations WHERE workspace_id=? AND revoked_at IS NULL AND accepted_at IS NULL AND expires_at>now()",
                        Integer.class,
                        workspace)
                >= 100)
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Revoke or use existing invitations before creating more");
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO answerbase.organization_invitations(id,organization_id,workspace_id,email,workspace_role,token_hash,invited_by) VALUES(?,?,?,?,?,?,?)",
                id,
                ws.organizationId(),
                workspace,
                email(raw),
                role.name(),
                hash(token),
                user);
        return new Created(id, token);
    }

    List<Map<String, Object>> list(UUID user, UUID workspace) {
        admin(user, workspace);
        return jdbc.queryForList(
                "SELECT id,email,workspace_role,created_at,expires_at,accepted_at,revoked_at FROM answerbase.organization_invitations WHERE workspace_id=? ORDER BY created_at DESC LIMIT 100",
                workspace);
    }

    @Transactional
    public void revoke(UUID user, UUID workspace, UUID id) {
        admin(user, workspace);
        jdbc.update(
                "UPDATE answerbase.organization_invitations SET revoked_at=now() WHERE workspace_id=? AND id=? AND accepted_at IS NULL",
                workspace,
                id);
    }

    @Transactional
    public UUID accept(UUID user, String verifiedEmail, String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid invitation");
        jdbc.queryForList(
                "SELECT pg_advisory_xact_lock(hashtextextended(?::text,0))", user.toString());
        if (!jdbc.queryForList("SELECT user_id FROM answerbase.staff_access WHERE user_id=?", user)
                .isEmpty())
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Staff identities cannot join customer organizations");
        var rows =
                jdbc.queryForList(
                        "SELECT *,expires_at>now() AS active FROM answerbase.organization_invitations WHERE token_hash=? FOR UPDATE",
                        hash(token));
        if (rows.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation unavailable");
        var invite = rows.getFirst();
        if (!email(verifiedEmail).equals(invite.get("email")))
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Sign in with the email address this invitation was sent to");
        if (invite.get("revoked_at") != null || !Boolean.TRUE.equals(invite.get("active")))
            throw new ResponseStatusException(HttpStatus.GONE, "Invitation expired or revoked");
        UUID org = (UUID) invite.get("organization_id"),
                workspace = (UUID) invite.get("workspace_id");
        var existing = organizations.find(user);
        if (existing != null && !existing.id().equals(org))
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This account already belongs to another organization. Use that company's identity instead.");
        if (invite.get("accepted_at") != null) {
            if (user.equals(invite.get("accepted_by"))) return workspace;
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invitation already accepted");
        }
        // Authorization to invite must still exist at acceptance, not merely when the link was
        // issued.
        jdbc.queryForList("SELECT id FROM answerbase.workspaces WHERE id=? FOR UPDATE", workspace);
        admin((UUID) invite.get("invited_by"), workspace);
        if (existing == null)
            jdbc.update(
                    "INSERT INTO answerbase.users(id,organization_id,organization_role,email) VALUES(?,?,'member',?)",
                    user,
                    org,
                    email(verifiedEmail));
        else
            jdbc.update(
                    "UPDATE answerbase.users SET email=? WHERE id=?", email(verifiedEmail), user);
        members.changeInOrganization(
                org,
                workspace,
                user,
                WorkspaceMembershipService.Role.valueOf((String) invite.get("workspace_role")));
        jdbc.update(
                "UPDATE answerbase.organization_invitations SET accepted_by=?,accepted_at=now() WHERE id=?",
                user,
                invite.get("id"));
        return workspace;
    }
}
