package com.answerbase.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Workspace query administration")
class QueryAdminController {
    record Correction(String answer) {}

    private final QueryAdminService service;
    private final DelegationService delegation;

    QueryAdminController(QueryAdminService service, DelegationService delegation) {
        this.service = service;
        this.delegation = delegation;
    }

    @GetMapping("/api/v1/workspaces/{workspace}/query-log")
    Object list(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID workspace,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "false") boolean unanswered) {
        return service.list(UUID.fromString(jwt.getSubject()), workspace, offset, unanswered);
    }

    @GetMapping("/api/v1/workspaces/{workspace}/usage")
    Object usage(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID workspace) {
        return service.usage(UUID.fromString(jwt.getSubject()), workspace);
    }

    @GetMapping("/api/v1/workspaces/{workspace}/query-log/{id}")
    Object detail(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID workspace, @PathVariable UUID id) {
        return service.detail(UUID.fromString(jwt.getSubject()), workspace, id);
    }

    @PutMapping("/api/v1/workspaces/{workspace}/query-log/{id}/correction")
    void correct(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID workspace,
            @PathVariable UUID id,
            @RequestBody Correction body) {
        service.correct(UUID.fromString(jwt.getSubject()), workspace, id, body.answer());
    }

    @GetMapping("/api/v1/workspaces/{workspace}/unanswered.csv")
    ResponseEntity<String> csv(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID workspace) {
        return download(service.unanswered(UUID.fromString(jwt.getSubject()), workspace));
    }

    private ResponseEntity<String> download(String value) {
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=unanswered.csv")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(value);
    }

    @GetMapping("/api/v1/delegated/workspaces/{workspace}/query-log")
    Object delegatedList(
            @RequestHeader("X-Support-Grant") String grant,
            @PathVariable UUID workspace,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "false") boolean unanswered) {
        return delegation.documentOperation(
                grant,
                workspace,
                "query-log.list",
                user -> service.list(user, workspace, offset, unanswered));
    }

    @GetMapping("/api/v1/delegated/workspaces/{workspace}/usage")
    Object delegatedUsage(
            @RequestHeader("X-Support-Grant") String grant, @PathVariable UUID workspace) {
        return delegation.documentOperation(
                grant, workspace, "usage.read", user -> service.usage(user, workspace));
    }

    @GetMapping("/api/v1/delegated/workspaces/{workspace}/query-log/{id}")
    Object delegatedDetail(
            @RequestHeader("X-Support-Grant") String grant,
            @PathVariable UUID workspace,
            @PathVariable UUID id) {
        return delegation.documentOperation(
                grant, workspace, "query-log.read", user -> service.detail(user, workspace, id));
    }

    @PutMapping("/api/v1/delegated/workspaces/{workspace}/query-log/{id}/correction")
    void delegatedCorrect(
            @RequestHeader("X-Support-Grant") String grant,
            @PathVariable UUID workspace,
            @PathVariable UUID id,
            @RequestBody Correction body) {
        delegation.documentOperation(
                grant,
                workspace,
                "query-log.correct",
                user -> {
                    service.correct(user, workspace, id, body.answer());
                    return null;
                });
    }

    @GetMapping("/api/v1/delegated/workspaces/{workspace}/unanswered.csv")
    ResponseEntity<String> delegatedCsv(
            @RequestHeader("X-Support-Grant") String grant, @PathVariable UUID workspace) {
        return download(
                delegation.documentOperation(
                        grant,
                        workspace,
                        "query-log.export",
                        user -> service.unanswered(user, workspace)));
    }
}
