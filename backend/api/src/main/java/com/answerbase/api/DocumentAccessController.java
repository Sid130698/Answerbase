package com.answerbase.api;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/workspaces/{workspace}")
@Tag(name = "Document permissions")
@SecurityRequirement(name = "bearerAuth")
class DocumentAccessController {
    record Name(String name) {}

    record Members(List<UUID> members) {}

    private final DocumentAccessService service;

    DocumentAccessController(DocumentAccessService service) {
        this.service = service;
    }

    @GetMapping("/groups")
    List<DocumentAccessService.Group> groups(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID workspace) {
        return service.groups(UUID.fromString(jwt.getSubject()), workspace);
    }

    @PostMapping("/groups")
    DocumentAccessService.Group create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID workspace,
            @RequestBody Name name) {
        return service.create(UUID.fromString(jwt.getSubject()), workspace, name.name());
    }

    @PutMapping("/groups/{group}/members")
    void members(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID workspace,
            @PathVariable UUID group,
            @RequestBody Members members) {
        service.members(UUID.fromString(jwt.getSubject()), workspace, group, members.members());
    }

    @GetMapping("/documents/{document}/access")
    DocumentAccessService.Policy access(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID workspace,
            @PathVariable UUID document) {
        return service.policy(UUID.fromString(jwt.getSubject()), workspace, document);
    }

    @PutMapping("/documents/{document}/access")
    DocumentAccessService.Policy access(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID workspace,
            @PathVariable UUID document,
            @RequestBody DocumentAccessService.Policy policy) {
        return service.policy(UUID.fromString(jwt.getSubject()), workspace, document, policy);
    }
}
