package com.answerbase.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/delegated")
@Tag(name = "Delegated customer session")
class DelegationController {
    private final DelegationService service;

    DelegationController(DelegationService service) {
        this.service = service;
    }

    @GetMapping("/me")
    @Operation(
            summary =
                    "Customer identity via opaque X-Support-Grant; a staff JWT alone is insufficient")
    IdentityController.Identity me(@RequestHeader("X-Support-Grant") String grant) {
        return service.identity(grant);
    }

    @GetMapping("/workspaces")
    List<WorkspaceService.Workspace> list(@RequestHeader("X-Support-Grant") String grant) {
        return service.list(grant);
    }

    @GetMapping("/workspaces/{id}")
    WorkspaceService.Workspace get(
            @RequestHeader("X-Support-Grant") String grant, @PathVariable UUID id) {
        return service.get(grant, id);
    }

    @PostMapping("/workspaces")
    ResponseEntity<WorkspaceService.Workspace> create(
            @RequestHeader("X-Support-Grant") String grant,
            @RequestHeader("Idempotency-Key") UUID key,
            @RequestBody WorkspaceController.CreateWorkspace body) {
        var result = service.create(grant, key, body.name());
        return ResponseEntity.status(result.created() ? 201 : 200).body(result.workspace());
    }
}
