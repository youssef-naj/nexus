package com.l2c.nexus.organization.api;

import com.l2c.nexus.membership.domain.OrgRole;
import com.l2c.nexus.organization.application.MyOrganization;
import com.l2c.nexus.organization.domain.OrganizationStatus;
import java.util.UUID;

public record OrganizationResponse(
        UUID id, String name, String slug, OrgRole role, OrganizationStatus status) {

    static OrganizationResponse from(MyOrganization organization) {
        return new OrganizationResponse(
                organization.id(),
                organization.name(),
                organization.slug(),
                organization.role(),
                organization.status());
    }
}
