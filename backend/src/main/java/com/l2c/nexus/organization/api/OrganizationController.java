package com.l2c.nexus.organization.api;

import com.l2c.nexus.organization.application.OrganizationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orgs")
class OrganizationController {

    private final OrganizationService organizations;

    OrganizationController(OrganizationService organizations) {
        this.organizations = organizations;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    OrganizationResponse create(
            Authentication authentication, @Valid @RequestBody CreateOrganizationRequest request) {
        return OrganizationResponse.from(
                organizations.create(userId(authentication), request.name()));
    }

    @GetMapping
    List<OrganizationResponse> mine(Authentication authentication) {
        return organizations.listFor(userId(authentication)).stream()
                .map(OrganizationResponse::from)
                .toList();
    }

    /**
     * The authentication name is the user ID (see AuthenticatedUser), so no identity types leak.
     */
    private static UUID userId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
