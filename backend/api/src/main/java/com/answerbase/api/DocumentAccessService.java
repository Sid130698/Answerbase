package com.answerbase.api;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class DocumentAccessService {
    record Group(UUID id, String name, List<UUID> members) {}

    record Policy(boolean restricted, List<UUID> groups) {}

    private final JdbcTemplate jdbc;
    private final WorkspaceService workspaces;

    DocumentAccessService(JdbcTemplate jdbc, WorkspaceService workspaces) {
        this.jdbc = jdbc;
        this.workspaces = workspaces;
    }

    WorkspaceService.Workspace admin(UUID user, UUID workspace) {
        var w = workspaces.get(user, workspace);
        if (!w.role().equals("admin"))
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Workspace admin access required");
        return w;
    }

    List<Group> groups(UUID user, UUID workspace) {
        admin(user, workspace);
        return jdbc.query(
                "SELECT id,name FROM answerbase.document_groups WHERE workspace_id=? ORDER BY name",
                (rs, n) ->
                        new Group(
                                rs.getObject("id", UUID.class),
                                rs.getString("name"),
                                jdbc.queryForList(
                                        "SELECT user_id FROM answerbase.group_members WHERE group_id=? ORDER BY user_id",
                                        UUID.class,
                                        rs.getObject("id", UUID.class))),
                workspace);
    }

    @Transactional
    public Group create(UUID user, UUID workspace, String raw) {
        var w = admin(user, workspace);
        String name = raw == null ? "" : raw.strip();
        if (name.isEmpty()
                || name.length() > 80
                || name.codePoints().anyMatch(Character::isISOControl))
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Use a group name of 1–80 characters");
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO answerbase.document_groups(id,organization_id,workspace_id,name) VALUES (?,?,?,?) ON CONFLICT(workspace_id,name) DO NOTHING",
                id,
                w.organizationId(),
                workspace,
                name);
        return groups(user, workspace).stream()
                .filter(g -> g.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    @Transactional
    public void members(UUID user, UUID workspace, UUID group, List<UUID> members) {
        var w = admin(user, workspace);
        if (members == null || members.size() > 200)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide up to 200 members");
        if (jdbc.queryForList(
                        "SELECT id FROM answerbase.document_groups WHERE id=? AND workspace_id=? FOR UPDATE",
                        group,
                        workspace)
                .isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found");
        for (var member : new HashSet<>(members))
            if (jdbc.queryForList(
                            "SELECT user_id FROM answerbase.workspace_members WHERE organization_id=? AND workspace_id=? AND user_id=?",
                            w.organizationId(),
                            workspace,
                            member)
                    .isEmpty())
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Workspace member not found");
        jdbc.update("DELETE FROM answerbase.group_members WHERE group_id=?", group);
        for (var member : new HashSet<>(members))
            jdbc.update(
                    "INSERT INTO answerbase.group_members(organization_id,workspace_id,group_id,user_id) VALUES (?,?,?,?)",
                    w.organizationId(),
                    workspace,
                    group,
                    member);
    }

    Policy policy(UUID user, UUID workspace, UUID document) {
        admin(user, workspace);
        var values =
                jdbc.queryForList(
                        "SELECT restricted FROM answerbase.documents WHERE id=? AND workspace_id=?",
                        Boolean.class,
                        document,
                        workspace);
        if (values.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found");
        return new Policy(
                values.getFirst(),
                jdbc.queryForList(
                        "SELECT group_id FROM answerbase.document_group_access WHERE document_id=? ORDER BY group_id",
                        UUID.class,
                        document));
    }

    @Transactional
    public Policy policy(UUID user, UUID workspace, UUID document, Policy policy) {
        var w = admin(user, workspace);
        policy(user, workspace, document);
        if (policy.groups() == null || policy.groups().size() > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide up to 100 groups");
        jdbc.queryForList("SELECT id FROM answerbase.documents WHERE id=? FOR UPDATE", document);
        for (var group : policy.groups())
            if (jdbc.queryForList(
                            "SELECT id FROM answerbase.document_groups WHERE id=? AND workspace_id=?",
                            group,
                            workspace)
                    .isEmpty())
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found");
        jdbc.update(
                "UPDATE answerbase.documents SET restricted=? WHERE id=?",
                policy.restricted(),
                document);
        jdbc.update("DELETE FROM answerbase.document_group_access WHERE document_id=?", document);
        for (var group : new HashSet<>(policy.groups()))
            jdbc.update(
                    "INSERT INTO answerbase.document_group_access(organization_id,workspace_id,document_id,group_id) VALUES (?,?,?,?)",
                    w.organizationId(),
                    workspace,
                    document,
                    group);
        return policy(user, workspace, document);
    }
}
