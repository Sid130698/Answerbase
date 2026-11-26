package com.answerbase.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Document lifecycle")
class LifecycleController {
    private final LifecycleService service;
    private final DelegationService delegation;

    LifecycleController(LifecycleService service, DelegationService delegation) {
        this.service = service;
        this.delegation = delegation;
    }

    @DeleteMapping("/api/v1/workspaces/{workspace}/documents/{document}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID workspace,
            @PathVariable UUID document) {
        service.delete(UUID.fromString(jwt.getSubject()), workspace, document);
    }

    @GetMapping("/api/v1/workspaces/{workspace}/retention")
    Object policy(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID workspace) {
        return service.retention(UUID.fromString(jwt.getSubject()), workspace);
    }

    @PutMapping("/api/v1/workspaces/{workspace}/retention")
    void policy(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID workspace,
            @RequestBody LifecycleService.Retention policy) {
        service.retention(UUID.fromString(jwt.getSubject()), workspace, policy);
    }

    @GetMapping("/api/v1/workspaces/{workspace}/deletions")
    Object pending(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID workspace) {
        return service.pending(UUID.fromString(jwt.getSubject()), workspace);
    }

    @DeleteMapping("/api/v1/delegated/workspaces/{workspace}/documents/{document}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void delegatedDelete(
            @RequestHeader("X-Support-Grant") String grant,
            @PathVariable UUID workspace,
            @PathVariable UUID document) {
        delegation.documentOperation(
                grant,
                workspace,
                "document.delete",
                user -> {
                    service.delete(user, workspace, document);
                    return null;
                });
    }

    @GetMapping("/api/v1/delegated/workspaces/{workspace}/retention")
    Object delegatedPolicy(
            @RequestHeader("X-Support-Grant") String grant, @PathVariable UUID workspace) {
        return delegation.documentOperation(
                grant, workspace, "retention.read", user -> service.retention(user, workspace));
    }

    @PutMapping("/api/v1/delegated/workspaces/{workspace}/retention")
    void delegatedPolicy(
            @RequestHeader("X-Support-Grant") String grant,
            @PathVariable UUID workspace,
            @RequestBody LifecycleService.Retention policy) {
        delegation.documentOperation(
                grant,
                workspace,
                "retention.update",
                user -> {
                    service.retention(user, workspace, policy);
                    return null;
                });
    }

    @GetMapping("/api/v1/delegated/workspaces/{workspace}/deletions")
    Object delegatedPending(
            @RequestHeader("X-Support-Grant") String grant, @PathVariable UUID workspace) {
        return delegation.documentOperation(
                grant, workspace, "deletions.read", user -> service.pending(user, workspace));
    }
}
