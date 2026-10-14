package com.l2c.nexus.organization.application;

import com.l2c.nexus.organization.domain.OrganizationStatus;
import java.time.Instant;
import java.util.UUID;

/** Everything the platform may know about an organization: metadata only (ADR-0006). */
public record OrganizationAdminView(
        UUID id,
        String name,
        String slug,
        OrganizationStatus status,
        long memberCount,
        Instant createdAt,
        long version) {}
