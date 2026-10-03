package com.l2c.nexus.organization.api;

import com.l2c.nexus.membership.domain.OrgRole;
import com.l2c.nexus.organization.domain.OrganizationStatus;
import java.util.List;
import java.util.UUID;

/** The organization as seen by one member: their role and what that role allows (for the UI). */
public record OrganizationDetailResponse(
        UUID id,
        String name,
        String slug,
        OrganizationStatus status,
        OrgRole role,
        List<String> permissions) {}
