package com.l2c.nexus.team.persistence;

import com.l2c.nexus.team.domain.Invitation;
import com.l2c.nexus.team.domain.InvitationStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

    Optional<Invitation> findByTokenHash(String tokenHash);

    /** Always scoped by organization, so an id from another tenant simply is not found. */
    Optional<Invitation> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<Invitation> findByOrganizationIdAndStatusOrderByCreatedAtDesc(
            UUID organizationId, InvitationStatus status);

    List<Invitation> findByOrganizationIdAndEmailAndStatus(
            UUID organizationId, String email, InvitationStatus status);

    /** Atomic status change. Returns 1 only for the single caller that wins. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            "update Invitation i set i.status = :to, i.decidedAt = :now"
                    + " where i.id = :id and i.status = :from")
    int transition(
            @Param("id") UUID id,
            @Param("from") InvitationStatus from,
            @Param("to") InvitationStatus to,
            @Param("now") Instant now);

    /** Like transition, but only while the invitation has not expired. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            "update Invitation i set i.status = :to, i.decidedAt = :now"
                    + " where i.id = :id and i.status = :from and i.expiresAt > :now")
    int transitionIfUnexpired(
            @Param("id") UUID id,
            @Param("from") InvitationStatus from,
            @Param("to") InvitationStatus to,
            @Param("now") Instant now);
}
