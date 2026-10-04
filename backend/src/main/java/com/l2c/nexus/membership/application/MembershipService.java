package com.l2c.nexus.membership.application;

import com.l2c.nexus.membership.domain.Membership;
import com.l2c.nexus.membership.domain.MembershipStatus;
import com.l2c.nexus.membership.domain.OrgRole;
import com.l2c.nexus.membership.persistence.MembershipRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The membership module's public API. Other modules call this, never its repository. */
@Service
public class MembershipService {

    private final MembershipRepository memberships;
    private final Clock clock;

    public MembershipService(MembershipRepository memberships, Clock clock) {
        this.memberships = memberships;
        this.clock = clock;
    }

    @Transactional
    public MembershipView addMember(UUID organizationId, UUID userId, OrgRole role) {
        Membership saved =
                memberships.save(Membership.create(organizationId, userId, role, clock.instant()));
        return view(saved);
    }

    /** Adds the user, or reactivates their revoked membership. Fails if already active. */
    @Transactional
    public MembershipView addOrReactivate(UUID organizationId, UUID userId, OrgRole role) {
        Instant now = clock.instant();
        Membership membership =
                memberships
                        .findByOrganizationIdAndUserId(organizationId, userId)
                        .map(
                                existing -> {
                                    if (existing.getStatus() == MembershipStatus.ACTIVE) {
                                        throw new IllegalStateException("Already an active member");
                                    }
                                    existing.reactivate(role, now);
                                    return existing;
                                })
                        .orElseGet(
                                () ->
                                        memberships.save(
                                                Membership.create(
                                                        organizationId, userId, role, now)));
        return view(membership);
    }

    @Transactional(readOnly = true)
    public List<MembershipView> activeMembershipsOf(UUID userId) {
        return memberships.findByUserIdAndStatus(userId, MembershipStatus.ACTIVE).stream()
                .map(MembershipService::view)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<MembershipView> findActiveMembership(UUID organizationId, UUID userId) {
        return memberships
                .findByOrganizationIdAndUserIdAndStatus(
                        organizationId, userId, MembershipStatus.ACTIVE)
                .map(MembershipService::view);
    }

    /** A membership of THIS organization, active or revoked. */
    @Transactional(readOnly = true)
    public Optional<MembershipView> find(UUID organizationId, UUID membershipId) {
        return memberships
                .findByIdAndOrganizationId(membershipId, organizationId)
                .map(MembershipService::view);
    }

    @Transactional
    public MembershipView changeRole(UUID organizationId, UUID membershipId, OrgRole newRole) {
        Membership membership = require(organizationId, membershipId);
        membership.changeRole(newRole, clock.instant());
        return view(memberships.saveAndFlush(membership));
    }

    @Transactional
    public void revoke(UUID organizationId, UUID membershipId) {
        Membership membership = require(organizationId, membershipId);
        membership.revoke(clock.instant());
        memberships.saveAndFlush(membership);
    }

    @Transactional(readOnly = true)
    public long countActiveByRole(UUID organizationId, OrgRole role) {
        return memberships.countByOrganizationIdAndRoleAndStatus(
                organizationId, role, MembershipStatus.ACTIVE);
    }

    private Membership require(UUID organizationId, UUID membershipId) {
        return memberships
                .findByIdAndOrganizationId(membershipId, organizationId)
                .orElseThrow(() -> new IllegalStateException("Membership not found"));
    }

    private static MembershipView view(Membership membership) {
        return new MembershipView(
                membership.getId(),
                membership.getOrganizationId(),
                membership.getUserId(),
                membership.getRole(),
                membership.getStatus(),
                membership.getVersion());
    }
}
