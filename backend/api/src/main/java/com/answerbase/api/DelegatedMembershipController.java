package com.answerbase.api;

import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/delegated/workspaces/{workspace}/members")
class DelegatedMembershipController {
    private final WorkspaceMembershipService service;
    private final DelegationService delegation;

    DelegatedMembershipController(
            WorkspaceMembershipService service, DelegationService delegation) {
        this.service = service;
        this.delegation = delegation;
    }

    @GetMapping
    List<WorkspaceMembershipService.Member> list(
            @RequestHeader("X-Support-Grant") String grant, @PathVariable UUID workspace) {
        return delegation.documentOperation(
                grant, workspace, "members.list", user -> service.list(user, workspace));
    }

    @PutMapping("/{target}")
    void change(
            @RequestHeader("X-Support-Grant") String grant,
            @PathVariable UUID workspace,
            @PathVariable UUID target,
            @RequestBody WorkspaceMembershipController.Assignment body) {
        if (body.role() == null)
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Role required");
        delegation.documentOperation(
                grant,
                workspace,
                "members.change",
                user -> {
                    service.change(user, workspace, target, body.role());
                    return null;
                });
    }

    @DeleteMapping("/{target}")
    void remove(
            @RequestHeader("X-Support-Grant") String grant,
            @PathVariable UUID workspace,
            @PathVariable UUID target) {
        delegation.documentOperation(
                grant,
                workspace,
                "members.remove",
                user -> {
                    service.change(user, workspace, target, null);
                    return null;
                });
    }
}
