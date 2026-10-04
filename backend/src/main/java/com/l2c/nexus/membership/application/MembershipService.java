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

    @Transactional(readOnly = true)
    public List<MembershipView> activeMembershipsOf(UUID userId) {
        return memberships.findByUserIdAndStatus(userId, MembershipStatus.ACTIVE).stream()
                .map(MembershipService::view)
                .toList();
    }

    private static MembershipView view(Membership membership) {
        return new MembershipView(
                membership.getId(),
                membership.getOrganizationId(),
                membership.getUserId(),
                membership.getRole());
    }

    @Transactional(readOnly = true)
    public Optional<MembershipView> findActiveMembership(UUID organizationId, UUID userId) {
        return memberships
                .findByOrganizationIdAndUserIdAndStatus(
                        organizationId, userId, MembershipStatus.ACTIVE)
                .map(MembershipService::view);
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
}
