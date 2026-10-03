package com.l2c.nexus.organization.application;

import com.l2c.nexus.membership.domain.OrgRole;
import com.l2c.nexus.organization.domain.OrganizationStatus;
import java.util.UUID;

/** An organization as seen by one user: it includes THAT user's role in it. */
public record MyOrganization(
        UUID id, String name, String slug, OrgRole role, OrganizationStatus status) {}
