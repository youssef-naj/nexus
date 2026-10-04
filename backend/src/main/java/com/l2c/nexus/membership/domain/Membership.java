package com.l2c.nexus.membership.domain;

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

/**
 * A user's place in an organization. Never deleted, only revoked, so history that refers to it
 * stays valid. References are plain IDs, not object links, so modules stay decoupled.
 */
@Entity
@Table(name = "memberships")
public class Membership {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrgRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MembershipStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version private long version;

    /** Required by JPA. Use {@link #create}. */
    protected Membership() {}

    public static Membership create(UUID organizationId, UUID userId, OrgRole role, Instant now) {
        Membership membership = new Membership();
        membership.organizationId = organizationId;
        membership.userId = userId;
        membership.role = role;
        membership.status = MembershipStatus.ACTIVE;
        membership.createdAt = now;
        membership.updatedAt = now;
        return membership;
    }

    /** A revoked member rejoins: the same row is reused, with the new role. */
    public void reactivate(OrgRole newRole, Instant now) {
        this.role = newRole;
        this.status = MembershipStatus.ACTIVE;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getUserId() {
        return userId;
    }

    public OrgRole getRole() {
        return role;
    }

    public MembershipStatus getStatus() {
        return status;
    }
}
