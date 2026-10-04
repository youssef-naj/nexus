package com.l2c.nexus.team.domain;

import com.l2c.nexus.membership.domain.OrgRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** An offer to join an organization. Only the hash of its token is stored. */
@Entity
@Table(name = "invitations")
public class Invitation {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 320, updatable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private OrgRole role;

    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvitationStatus status;

    @Column(name = "invited_by_membership_id", nullable = false, updatable = false)
    private UUID invitedByMembershipId;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Version private long version;

    /** Required by JPA. Use {@link #create}. */
    protected Invitation() {}

    public static Invitation create(
            UUID organizationId,
            String email,
            OrgRole role,
            String tokenHash,
            UUID invitedByMembershipId,
            Instant now,
            Instant expiresAt) {
        Invitation invitation = new Invitation();
        invitation.organizationId = organizationId;
        invitation.email = email;
        invitation.role = role;
        invitation.tokenHash = tokenHash;
        invitation.invitedByMembershipId = invitedByMembershipId;
        invitation.status = InvitationStatus.PENDING;
        invitation.createdAt = now;
        invitation.expiresAt = expiresAt;
        return invitation;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getEmail() {
        return email;
    }

    public OrgRole getRole() {
        return role;
    }

    public InvitationStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
