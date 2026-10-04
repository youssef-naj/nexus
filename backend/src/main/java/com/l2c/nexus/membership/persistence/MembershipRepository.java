package com.l2c.nexus.membership.persistence;

import com.l2c.nexus.membership.domain.Membership;
import com.l2c.nexus.membership.domain.MembershipStatus;
import com.l2c.nexus.membership.domain.OrgRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {

    List<Membership> findByUserIdAndStatus(UUID userId, MembershipStatus status);

    Optional<Membership> findByOrganizationIdAndUserIdAndStatus(
            UUID organizationId, UUID userId, MembershipStatus status);

    Optional<Membership> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);

    /** Always scoped by organization: a membership id from another tenant is simply not found. */
    Optional<Membership> findByIdAndOrganizationId(UUID id, UUID organizationId);

    long countByOrganizationIdAndRoleAndStatus(
            UUID organizationId, OrgRole role, MembershipStatus status);
}
