package com.answerbase.api;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Search")
@SecurityRequirement(name = "bearerAuth")
class SearchController {
    record Search(String question) {}

    private final RetrievalService service;
    private final DelegationService delegation;

    SearchController(RetrievalService service, DelegationService delegation) {
        this.service = service;
        this.delegation = delegation;
    }

    @PostMapping("/api/v1/workspaces/{workspace}/search")
    RetrievalService.Result search(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID workspace,
            @RequestBody Search body) {
        return service.search(UUID.fromString(jwt.getSubject()), workspace, body.question());
    }

    @PostMapping("/api/v1/delegated/workspaces/{workspace}/search")
    RetrievalService.Result delegated(
            @RequestHeader("X-Support-Grant") String grant,
            @PathVariable UUID workspace,
            @RequestBody Search body) {
        return delegation.documentOperation(
                grant,
                workspace,
                "search",
                user -> service.search(user, workspace, body.question()));
    }
}
