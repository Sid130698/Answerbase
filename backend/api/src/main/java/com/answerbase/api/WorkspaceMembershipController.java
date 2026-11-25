package com.answerbase.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/workspaces/{workspace}/members")
@Tag(name = "Workspace membership")
@SecurityRequirement(name = "bearerAuth")
class WorkspaceMembershipController {
    record Assignment(WorkspaceMembershipService.Role role) {}

    private final WorkspaceMembershipService service;

    WorkspaceMembershipController(WorkspaceMembershipService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List workspace members (workspace admin only)")
    List<WorkspaceMembershipService.Member> list(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID workspace) {
        return service.list(UUID.fromString(jwt.getSubject()), workspace);
    }

    @PutMapping("/{user}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Assign a workspace role to an existing organization member",
            description =
                    "Workspace admin only. Roles are admin, editor, viewer. A user from another organization returns 404. Removing the last admin role returns 409. Organization roles never bypass workspace membership.")
    void assign(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID workspace,
            @PathVariable UUID user,
            @RequestBody Assignment assignment) {
        if (assignment.role() == null)
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "A workspace role is required");
        service.change(UUID.fromString(jwt.getSubject()), workspace, user, assignment.role());
    }

    @DeleteMapping("/{user}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Revoke workspace access (workspace admin only)",
            description =
                    "Cannot remove the last admin. This does not remove the user's organization account.")
    void remove(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID workspace,
            @PathVariable UUID user) {
        service.change(UUID.fromString(jwt.getSubject()), workspace, user, null);
    }
}
