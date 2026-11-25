package com.answerbase.api;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/delegated/workspaces/{workspace}")
@Tag(name = "Document permissions")
@SecurityRequirement(name = "bearerAuth")
class DelegatedDocumentAccessController {
    record Name(String name) {}

    record Members(List<UUID> members) {}

    private final DocumentAccessService service;

    DelegatedDocumentAccessController(DocumentAccessService service, DelegationService delegation) {
        this.service = service;
        this.delegation = delegation;
    }

    private final DelegationService delegation;

    @GetMapping("/groups")
    List<DocumentAccessService.Group> groups(
            @RequestHeader("X-Support-Grant") String grant, @PathVariable UUID workspace) {
        return delegation.documentOperation(
                grant, workspace, "groups.list", user -> service.groups(user, workspace));
    }

    @PostMapping("/groups")
    DocumentAccessService.Group create(
            @RequestHeader("X-Support-Grant") String grant,
            @PathVariable UUID workspace,
            @RequestBody Name name) {
        return delegation.documentOperation(
                grant,
                workspace,
                "groups.create",
                user -> service.create(user, workspace, name.name()));
    }

    @PutMapping("/groups/{group}/members")
    void members(
            @RequestHeader("X-Support-Grant") String grant,
            @PathVariable UUID workspace,
            @PathVariable UUID group,
            @RequestBody Members members) {
        delegation.documentOperation(
                grant,
                workspace,
                "groups.members",
                user -> {
                    service.members(user, workspace, group, members.members());
                    return null;
                });
    }

    @GetMapping("/documents/{document}/access")
    DocumentAccessService.Policy access(
            @RequestHeader("X-Support-Grant") String grant,
            @PathVariable UUID workspace,
            @PathVariable UUID document) {
        return delegation.documentOperation(
                grant, workspace, "access.read", user -> service.policy(user, workspace, document));
    }

    @PutMapping("/documents/{document}/access")
    DocumentAccessService.Policy access(
            @RequestHeader("X-Support-Grant") String grant,
            @PathVariable UUID workspace,
            @PathVariable UUID document,
            @RequestBody DocumentAccessService.Policy policy) {
        return delegation.documentOperation(
                grant,
                workspace,
                "access.change",
                user -> service.policy(user, workspace, document, policy));
    }
}
