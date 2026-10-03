package com.l2c.nexus.organization.application;

import com.l2c.nexus.membership.application.MembershipService;
import com.l2c.nexus.organization.persistence.OrganizationRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrgAccessService {

    private final MembershipService memberships;
    private final OrganizationRepository organizations;

    public OrgAccessService(MembershipService memberships, OrganizationRepository organizations) {
        this.memberships = memberships;
        this.organizations = organizations;
    }

    /**
     * The caller's context in the organization, or empty if they are not an ACTIVE member. The
     * membership is looked up first, so an unknown organization costs the same as one the caller
     * does not belong to.
     */
    @Transactional(readOnly = true)
    public Optional<OrgContext> resolve(UUID userId, UUID organizationId) {
        return memberships
                .findActiveMembership(organizationId, userId)
                .flatMap(
                        membership ->
                                organizations
                                        .findById(organizationId)
                                        .map(
                                                org ->
                                                        new OrgContext(
                                                                org.getId(),
                                                                userId,
                                                                membership.id(),
                                                                membership.role(),
                                                                org.getStatus())));
    }
}
