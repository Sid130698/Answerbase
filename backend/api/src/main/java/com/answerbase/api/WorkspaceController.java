package com.answerbase.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/workspaces")
@Tag(name = "Workspaces")
@SecurityRequirement(name = "bearerAuth")
class WorkspaceController {
    record CreateWorkspace(String name) {}

    private final WorkspaceService service;

    WorkspaceController(WorkspaceService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List workspaces you belong to")
    List<WorkspaceService.Workspace> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Read a workspace",
            description = "Returns 404 when the workspace does not exist or you are not a member.")
    WorkspaceService.Workspace get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.get(UUID.fromString(jwt.getSubject()), id);
    }

    @PostMapping
    @Operation(
            summary = "Create a workspace with yourself as admin",
            description =
                    "Organization owner/admin only; organization is resolved from the account, never the request. Requires a UUID Idempotency-Key header. Reuse it with the same name to safely retry (200); a new workspace returns 201. A changed name with the same key or the configured organization workspace quota returns 409. Invalid input returns 400.")
    ResponseEntity<WorkspaceService.Workspace> create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") UUID key,
            @RequestBody CreateWorkspace request) {
        var result = service.create(UUID.fromString(jwt.getSubject()), key, request.name());
        return ResponseEntity.status(result.created() ? 201 : 200)
                .location(URI.create("/api/v1/workspaces/" + result.workspace().id()))
                .body(result.workspace());
    }
}
