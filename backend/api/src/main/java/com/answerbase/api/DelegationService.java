package com.answerbase.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class DelegationService {
    record Grant(String token, Instant expiresAt, UUID userId) {}

    record Context(UUID actor, UUID session, UUID organization, UUID target) {}

    private final JdbcTemplate jdbc;
    private final SupportService support;
    private final WorkspaceService workspaces;
    private final OrganizationService organizations;

    DelegationService(
            JdbcTemplate jdbc,
            SupportService support,
            WorkspaceService workspaces,
            OrganizationService organizations) {
        this.jdbc = jdbc;
        this.support = support;
        this.workspaces = workspaces;
        this.organizations = organizations;
    }

    private String hash(String token) {
        if (token == null || token.length() != 43)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid support grant");
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Transactional
    public <T> T documentOperation(
            String token,
            UUID workspace,
            String action,
            java.util.function.Function<UUID, T> operation) {
        var c = resolve(token);
        workspaces.get(c.target(), workspace);
        T result = operation.apply(c.target());
        support.audit(
                c.actor(),
                c.session(),
                c.organization(),
                c.target(),
                "delegated.document." + action,
                "workspace=" + workspace);
        return result;
    }

    @Transactional
    public Grant issue(UUID actor, UUID sessionId, UUID target) {
        var session = support.requireSession(actor, sessionId);
        support.requireTarget(session, target);
        jdbc.update(
                "UPDATE answerbase.support_grants SET revoked_at=now() WHERE session_id=? AND revoked_at IS NULL",
                sessionId);
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expires = Instant.now().plusSeconds(600);
        if (expires.isAfter(session.expiresAt())) expires = session.expiresAt();
        jdbc.update(
                "INSERT INTO answerbase.support_grants (token_hash,session_id,target_user_id,expires_at) VALUES (?,?,?,?)",
                hash(token),
                sessionId,
                target,
                java.sql.Timestamp.from(expires));
        support.audit(
                actor,
                sessionId,
                session.organizationId(),
                target,
                "delegation.issue",
                "Ten-minute customer-permission grant; previous grants revoked");
        return new Grant(token, expires, target);
    }

    private Context resolve(String token) {
        var rows =
                jdbc.query(
                        """
                SELECT s.actor_id,s.id,s.organization_id,g.target_user_id
                FROM answerbase.support_grants g JOIN answerbase.support_sessions s ON s.id=g.session_id
                JOIN answerbase.staff_access a ON a.user_id=s.actor_id AND a.active
                JOIN answerbase.users u ON u.id=g.target_user_id AND u.organization_id=s.organization_id
                WHERE g.token_hash=? AND g.revoked_at IS NULL AND g.expires_at>now()
                  AND s.closed_at IS NULL AND s.expires_at>now()
                FOR UPDATE OF s
                """,
                        (rs, row) ->
                                new Context(
                                        rs.getObject("actor_id", UUID.class),
                                        rs.getObject("id", UUID.class),
                                        rs.getObject("organization_id", UUID.class),
                                        rs.getObject("target_user_id", UUID.class)),
                        hash(token));
        if (rows.isEmpty())
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Support access expired or ended");
        // Re-check after the session lock: switching targets may have revoked this grant while
        // waiting.
        if (jdbc.queryForList(
                        "SELECT token_hash FROM answerbase.support_grants WHERE token_hash=? AND revoked_at IS NULL AND expires_at>now()",
                        hash(token))
                .isEmpty())
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Support access expired or ended");
        return rows.getFirst();
    }

    @Transactional
    public IdentityController.Identity identity(String token) {
        var c = resolve(token);
        support.audit(
                c.actor(),
                c.session(),
                c.organization(),
                c.target(),
                "delegated.identity",
                "Read selected user's application identity");
        return new IdentityController.Identity(
                c.target().toString(), "User " + c.target(), organizations.require(c.target()));
    }

    @Transactional
    public List<WorkspaceService.Workspace> list(String token) {
        var c = resolve(token);
        var result = workspaces.list(c.target());
        support.audit(
                c.actor(),
                c.session(),
                c.organization(),
                c.target(),
                "delegated.workspaces.list",
                "Read with customer membership permissions");
        return result;
    }

    @Transactional
    public WorkspaceService.Workspace get(String token, UUID id) {
        var c = resolve(token);
        var result = workspaces.get(c.target(), id);
        support.audit(
                c.actor(),
                c.session(),
                c.organization(),
                c.target(),
                "delegated.workspace.read",
                id.toString());
        return result;
    }

    @Transactional
    public WorkspaceService.Creation create(String token, UUID key, String name) {
        var c = resolve(token);
        var result = workspaces.create(c.target(), key, name);
        support.audit(
                c.actor(),
                c.session(),
                c.organization(),
                c.target(),
                "delegated.workspace.create",
                result.workspace().id() + "; created=" + result.created());
        return result;
    }
}
