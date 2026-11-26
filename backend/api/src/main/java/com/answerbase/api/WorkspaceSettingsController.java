package com.answerbase.api;

import com.answerbase.core.models.WorkspacePolicy;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Workspace model settings")
class WorkspaceSettingsController {
    private final WorkspaceSettingsService service;
    private final DelegationService delegation;

    WorkspaceSettingsController(WorkspaceSettingsService service, DelegationService delegation) {
        this.service = service;
        this.delegation = delegation;
    }

    @GetMapping("/api/v1/workspaces/{workspace}/settings")
    Object get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID workspace) {
        return service.get(UUID.fromString(jwt.getSubject()), workspace);
    }

    @PutMapping("/api/v1/workspaces/{workspace}/settings")
    void save(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID workspace,
            @RequestBody WorkspacePolicy p) {
        service.save(UUID.fromString(jwt.getSubject()), workspace, p);
    }

    @GetMapping("/api/v1/delegated/workspaces/{workspace}/settings")
    Object delegatedGet(
            @RequestHeader("X-Support-Grant") String grant, @PathVariable UUID workspace) {
        return delegation.documentOperation(
                grant, workspace, "settings.read", user -> service.get(user, workspace));
    }

    @PutMapping("/api/v1/delegated/workspaces/{workspace}/settings")
    void delegatedSave(
            @RequestHeader("X-Support-Grant") String grant,
            @PathVariable UUID workspace,
            @RequestBody WorkspacePolicy p) {
        delegation.documentOperation(
                grant,
                workspace,
                "settings.update",
                user -> {
                    service.save(user, workspace, p);
                    return null;
                });
    }
}
