package com.answerbase.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class IdentityController {
    private final OrganizationService organizations;

    IdentityController(OrganizationService organizations) {
        this.organizations = organizations;
    }

    @GetMapping("/api/v1/me")
    @Operation(
            summary = "Get the authenticated identity",
            description =
                    "Validates a Supabase access token. Identity does not grant workspace membership.",
            security = @SecurityRequirement(name = "bearerAuth"))
    Identity me(@AuthenticationPrincipal Jwt jwt) {
        return new Identity(
                jwt.getSubject(),
                jwt.getClaimAsString("email"),
                organizations.find(java.util.UUID.fromString(jwt.getSubject())));
    }

    record Identity(String id, String email, OrganizationService.Organization organization) {}
}
