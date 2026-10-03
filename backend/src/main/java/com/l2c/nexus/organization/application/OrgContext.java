package com.l2c.nexus.organization.application;

import com.l2c.nexus.membership.domain.OrgRole;
import com.l2c.nexus.organization.domain.OrganizationStatus;
import java.util.UUID;

/** Who is calling, in which organization, with which role. Produced only by the gate. */
public record OrgContext(
        UUID organizationId,
        UUID userId,
        UUID membershipId,
        OrgRole role,
        OrganizationStatus status) {}
