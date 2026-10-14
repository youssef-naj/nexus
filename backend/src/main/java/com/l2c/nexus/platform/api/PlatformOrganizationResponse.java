package com.l2c.nexus.platform.api;

import com.l2c.nexus.organization.application.OrganizationAdminView;
import com.l2c.nexus.organization.domain.OrganizationStatus;
import java.time.Instant;
import java.util.UUID;

/** Metadata only: no member names, emails, requests or any other tenant data (ADR-0006). */
public record PlatformOrganizationResponse(
        UUID id,
        String name,
        String slug,
        OrganizationStatus status,
        long memberCount,
        Instant createdAt,
        long version) {

    static PlatformOrganizationResponse from(OrganizationAdminView view) {
        return new PlatformOrganizationResponse(
                view.id(),
                view.name(),
                view.slug(),
                view.status(),
                view.memberCount(),
                view.createdAt(),
                view.version());
    }
}
