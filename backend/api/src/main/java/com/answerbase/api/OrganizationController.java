package com.answerbase.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Organizations")
@SecurityRequirement(name = "bearerAuth")
class OrganizationController {
    record CreateOrganization(String name) {}

    private final OrganizationService service;

    OrganizationController(OrganizationService service) {
        this.service = service;
    }

    @PostMapping("/api/v1/organizations")
    @Operation(
            summary = "Create your organization",
            description =
                    "For accounts without an organization. Creates the organization and owner account atomically. Returns 200, including safe retries with the same name. An account already in another organization returns 409. Names are labels, not verified domain claims. Joining an existing organization requires separate administrator approval.")
    OrganizationService.Organization create(
            @AuthenticationPrincipal Jwt jwt, @RequestBody CreateOrganization request) {
        return service.create(UUID.fromString(jwt.getSubject()), request.name());
    }
}
