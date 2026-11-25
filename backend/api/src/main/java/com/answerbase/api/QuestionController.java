package com.answerbase.api;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@Tag(name = "Cited answers")
@SecurityRequirement(name = "bearerAuth")
class QuestionController {
    record Feedback(int vote, String reason, String note) {}

    private final QuestionService service;
    private final DelegationService delegation;
    private final WorkspaceService workspaces;

    QuestionController(
            QuestionService service, DelegationService delegation, WorkspaceService workspaces) {
        this.service = service;
        this.delegation = delegation;
        this.workspaces = workspaces;
    }

    @PostMapping("/api/v1/workspaces/{workspace}/questions")
    QuestionService.Result ask(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID workspace,
            @RequestBody QuestionService.Ask body) {
        return service.ask(UUID.fromString(jwt.getSubject()), workspace, body, stage -> {});
    }

    @PostMapping(
            value = "/api/v1/workspaces/{workspace}/questions/stream",
            produces = "text/event-stream")
    SseEmitter stream(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID workspace,
            @RequestBody QuestionService.Ask body) {
        UUID user = UUID.fromString(jwt.getSubject());
        workspaces.get(user, workspace);
        return stream(user, workspace, body, null);
    }

    @PostMapping(
            value = "/api/v1/delegated/workspaces/{workspace}/questions/stream",
            produces = "text/event-stream")
    SseEmitter delegated(
            @RequestHeader("X-Support-Grant") String grant,
            @PathVariable UUID workspace,
            @RequestBody QuestionService.Ask body) {
        UUID user = delegation.documentOperation(grant, workspace, "question.begin", id -> id);
        return stream(user, workspace, body, grant);
    }

    private SseEmitter stream(UUID user, UUID workspace, QuestionService.Ask body, String grant) {
        var emitter = new SseEmitter(420000L);
        Thread.startVirtualThread(
                () -> {
                    try {
                        var result =
                                service.ask(
                                        user,
                                        workspace,
                                        body,
                                        stage -> send(emitter, "status", Map.of("message", stage)));
                        if (grant != null)
                            delegation.documentOperation(
                                    grant, workspace, "question.complete", id -> id);
                        // Claims are emitted only after verification, never as untrusted model
                        // token drafts.
                        for (var claim : result.answer().claims()) send(emitter, "claim", claim);
                        send(emitter, "answer", result);
                        emitter.complete();
                    } catch (Exception e) {
                        try {
                            send(
                                    emitter,
                                    "failure",
                                    Map.of(
                                            "message",
                                            e
                                                            instanceof
                                                            org.springframework.web.server
                                                                            .ResponseStatusException
                                                                    status
                                                    ? Objects.toString(
                                                            status.getReason(), "Request failed")
                                                    : "Answer service unavailable. Retry the question."));
                        } finally {
                            emitter.complete();
                        }
                    }
                });
        return emitter;
    }

    private void send(SseEmitter emitter, String event, Object value) {
        try {
            emitter.send(SseEmitter.event().name(event).data(value));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Client disconnected");
        }
    }

    @GetMapping("/api/v1/workspaces/{workspace}/questions")
    List<Map<String, Object>> history(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID workspace,
            @RequestParam UUID conversation) {
        return service.history(UUID.fromString(jwt.getSubject()), workspace, conversation);
    }

    @GetMapping("/api/v1/workspaces/{workspace}/questions/{id}")
    QuestionService.Result read(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID workspace, @PathVariable UUID id) {
        return service.read(UUID.fromString(jwt.getSubject()), workspace, id);
    }

    @PostMapping("/api/v1/workspaces/{workspace}/questions/{id}/feedback")
    void feedback(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID workspace,
            @PathVariable UUID id,
            @RequestBody Feedback body) {
        service.feedback(
                UUID.fromString(jwt.getSubject()),
                workspace,
                id,
                body.vote(),
                body.reason(),
                body.note());
    }

    @GetMapping("/api/v1/delegated/workspaces/{workspace}/questions")
    List<Map<String, Object>> delegatedHistory(
            @RequestHeader("X-Support-Grant") String grant,
            @PathVariable UUID workspace,
            @RequestParam UUID conversation) {
        return delegation.documentOperation(
                grant,
                workspace,
                "question.history",
                user -> service.history(user, workspace, conversation));
    }

    @GetMapping("/api/v1/delegated/workspaces/{workspace}/questions/{id}")
    QuestionService.Result delegatedRead(
            @RequestHeader("X-Support-Grant") String grant,
            @PathVariable UUID workspace,
            @PathVariable UUID id) {
        return delegation.documentOperation(
                grant, workspace, "question.read", user -> service.read(user, workspace, id));
    }

    @PostMapping("/api/v1/delegated/workspaces/{workspace}/questions/{id}/feedback")
    void delegatedFeedback(
            @RequestHeader("X-Support-Grant") String grant,
            @PathVariable UUID workspace,
            @PathVariable UUID id,
            @RequestBody Feedback body) {
        delegation.documentOperation(
                grant,
                workspace,
                "question.feedback",
                user -> {
                    service.feedback(user, workspace, id, body.vote(), body.reason(), body.note());
                    return null;
                });
    }
}
