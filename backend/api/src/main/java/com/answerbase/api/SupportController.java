package com.answerbase.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/support")
@Tag(name = "Answerbase staff support")
@SecurityRequirement(name = "bearerAuth")
class SupportController {
    record Start(UUID organizationId, String reason) {}

    record Target(UUID userId) {}

    record Assignment(WorkspaceMembershipService.Role role) {}

    private final SupportService service;
    private final DelegationService delegation;

    SupportController(SupportService service, DelegationService delegation) {
        this.service = service;
        this.delegation = delegation;
    }

    @GetMapping("/me")
    @Operation(summary = "Verify Answerbase staff access (customer roles never qualify)")
    SupportService.Staff me(@AuthenticationPrincipal Jwt jwt) {
        return service.requireStaff(UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/organizations")
    List<SupportService.Organization> organizations(
            @AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "") String query) {
        return service.organizations(UUID.fromString(jwt.getSubject()), query);
    }

    @PostMapping("/sessions")
    SupportService.Session start(@AuthenticationPrincipal Jwt jwt, @RequestBody Start body) {
        if (body.organizationId() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Organization required");
        return service.start(
                UUID.fromString(jwt.getSubject()), body.organizationId(), body.reason());
    }

    @GetMapping("/sessions/{id}")
    SupportService.Snapshot snapshot(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.snapshot(UUID.fromString(jwt.getSubject()), id);
    }

    @DeleteMapping("/sessions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void close(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.close(UUID.fromString(jwt.getSubject()), id);
    }

    @PostMapping("/sessions/{id}/delegations")
    @Operation(
            summary =
                    "Issue a ten-minute user-scoped grant; revokes previous grants in this support session")
    DelegationService.Grant delegate(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestBody Target target) {
        if (target.userId() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User required");
        return delegation.issue(UUID.fromString(jwt.getSubject()), id, target.userId());
    }

    @PutMapping("/sessions/{id}/workspaces/{workspace}/members/{user}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary =
                    "Staff repair: grant/change workspace membership, independent of impersonated user permissions")
    void assign(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @PathVariable UUID workspace,
            @PathVariable UUID user,
            @RequestBody Assignment body) {
        if (body.role() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role required");
        service.changeMembership(
                UUID.fromString(jwt.getSubject()), id, workspace, user, body.role());
    }

    @DeleteMapping("/sessions/{id}/workspaces/{workspace}/members/{user}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @PathVariable UUID workspace,
            @PathVariable UUID user) {
        service.changeMembership(UUID.fromString(jwt.getSubject()), id, workspace, user, null);
    }

    @GetMapping("/audit")
    @Operation(
            summary = "Latest 100 audit events; support sees own events, platform admins see all")
    List<Map<String, Object>> audit(@AuthenticationPrincipal Jwt jwt) {
        return service.history(UUID.fromString(jwt.getSubject()));
    }
}
