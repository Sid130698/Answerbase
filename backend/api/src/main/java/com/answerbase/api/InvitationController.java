package com.answerbase.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Organization invitations")
class InvitationController {
    record Invite(String email, WorkspaceMembershipService.Role role) {}

    record Accept(String token) {}

    private final InvitationService invitations;
    private final VerifiedEmailProvider identities;
    private final WorkspaceMembershipService members;
    private final DelegationService delegation;

    InvitationController(
            InvitationService invitations,
            VerifiedEmailProvider identities,
            WorkspaceMembershipService members,
            DelegationService delegation) {
        this.invitations = invitations;
        this.identities = identities;
        this.members = members;
        this.delegation = delegation;
    }

    @PostMapping("/api/v1/workspaces/{workspace}/invitations")
    Object create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID workspace,
            @RequestBody Invite body) {
        return invitations.create(
                UUID.fromString(jwt.getSubject()), workspace, body.email(), body.role());
    }

    @GetMapping("/api/v1/workspaces/{workspace}/invitations")
    Object list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID workspace) {
        return invitations.list(UUID.fromString(jwt.getSubject()), workspace);
    }

    @DeleteMapping("/api/v1/workspaces/{workspace}/invitations/{id}")
    void revoke(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID workspace, @PathVariable UUID id) {
        invitations.revoke(UUID.fromString(jwt.getSubject()), workspace, id);
    }

    @PostMapping("/api/v1/invitations/accept")
    Object accept(@AuthenticationPrincipal Jwt jwt, @RequestBody Accept body) {
        return Map.of(
                "workspaceId",
                invitations.accept(
                        UUID.fromString(jwt.getSubject()), identities.email(jwt), body.token()));
    }

    @GetMapping("/api/v1/workspaces/{workspace}/eligible-members")
    Object eligible(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID workspace) {
        return members.eligible(UUID.fromString(jwt.getSubject()), workspace);
    }

    @PostMapping("/api/v1/delegated/workspaces/{workspace}/invitations")
    Object delegatedCreate(
            @RequestHeader("X-Support-Grant") String grant,
            @PathVariable UUID workspace,
            @RequestBody Invite body) {
        return delegation.documentOperation(
                grant,
                workspace,
                "invitation.create",
                user -> invitations.create(user, workspace, body.email(), body.role()));
    }

    @GetMapping("/api/v1/delegated/workspaces/{workspace}/invitations")
    Object delegatedList(
            @RequestHeader("X-Support-Grant") String grant, @PathVariable UUID workspace) {
        return delegation.documentOperation(
                grant, workspace, "invitation.list", user -> invitations.list(user, workspace));
    }

    @DeleteMapping("/api/v1/delegated/workspaces/{workspace}/invitations/{id}")
    void delegatedRevoke(
            @RequestHeader("X-Support-Grant") String grant,
            @PathVariable UUID workspace,
            @PathVariable UUID id) {
        delegation.documentOperation(
                grant,
                workspace,
                "invitation.revoke",
                user -> {
                    invitations.revoke(user, workspace, id);
                    return null;
                });
    }

    @GetMapping("/api/v1/delegated/workspaces/{workspace}/eligible-members")
    Object delegatedEligible(
            @RequestHeader("X-Support-Grant") String grant, @PathVariable UUID workspace) {
        return delegation.documentOperation(
                grant, workspace, "members.eligible", user -> members.eligible(user, workspace));
    }
}
