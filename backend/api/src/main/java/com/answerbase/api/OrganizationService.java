package com.answerbase.api;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
class OrganizationService {
    record Organization(UUID id, String name, String role) {
        boolean canCreateWorkspace() {
            return role.equals("owner") || role.equals("admin");
        }
    }

    private final JdbcTemplate jdbc;

    OrganizationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Organization find(UUID user) {
        var rows =
                jdbc.query(
                        """
                SELECT o.id, o.name, u.organization_role FROM answerbase.users u
                JOIN answerbase.organizations o ON o.id=u.organization_id WHERE u.id=?
                """,
                        (rs, row) ->
                                new Organization(
                                        rs.getObject("id", UUID.class),
                                        rs.getString("name"),
                                        rs.getString("organization_role")),
                        user);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    @org.springframework.transaction.annotation.Transactional
    public Organization create(UUID user, String rawName) {
        String name = rawName == null ? "" : rawName.strip();
        if (name.isEmpty()
                || name.length() > 100
                || name.codePoints().anyMatch(Character::isISOControl))
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Use an organization name of 1–100 characters without control characters");
        jdbc.queryForList(
                "SELECT pg_advisory_xact_lock(hashtextextended(?::text, 0))", user.toString());
        if (!jdbc.queryForList("SELECT user_id FROM answerbase.staff_access WHERE user_id=?", user)
                .isEmpty())
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Staff accounts cannot join customer organizations");
        var existing = find(user);
        if (existing != null) {
            // Retry-safe enrollment; an account can never create or join a second organization.
            if (existing.role().equals("owner") && existing.name().equals(name)) return existing;
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Your account already belongs to an organization");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO answerbase.organizations (id, name) VALUES (?, ?)", id, name);
        jdbc.update(
                "INSERT INTO answerbase.users (id, organization_id, organization_role) VALUES (?, ?, 'owner')",
                user,
                id);
        return find(user);
    }

    Organization require(UUID user) {
        var organization = find(user);
        if (organization == null)
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Your account has not been assigned to an organization. Contact your company administrator.");
        return organization;
    }
}
