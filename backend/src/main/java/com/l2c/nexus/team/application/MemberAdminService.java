package com.l2c.nexus.team.application;

import com.l2c.nexus.audit.application.AuditEvent;
import com.l2c.nexus.audit.application.AuditEventType;
import com.l2c.nexus.audit.application.AuditService;
import com.l2c.nexus.audit.application.AuditTargetType;
import com.l2c.nexus.membership.application.MembershipService;
import com.l2c.nexus.membership.application.MembershipView;
import com.l2c.nexus.membership.application.RoleAssignmentPolicy;
import com.l2c.nexus.membership.domain.MembershipStatus;
import com.l2c.nexus.membership.domain.OrgRole;
import com.l2c.nexus.organization.application.AccessPolicy;
import com.l2c.nexus.organization.application.OrgContext;
import com.l2c.nexus.organization.application.OrganizationService;
import com.l2c.nexus.organization.application.Permission;
import com.l2c.nexus.shared.error.ConflictException;
import com.l2c.nexus.shared.error.ForbiddenActionException;
import com.l2c.nexus.shared.error.NotFoundException;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Changing roles, removing members and leaving (ADR-0007, ADR-0022). Every operation first takes
 * the organization lock and re-reads the caller's own membership: the gate's snapshot may be stale
 * by the time we run.
 */
@Service
public class MemberAdminService {

    private final MembershipService memberships;
    private final OrganizationService organizations;
    private final AccessPolicy policy;
    private final RoleAssignmentPolicy roles;
    private final AuditService audit;

    public MemberAdminService(
            MembershipService memberships,
            OrganizationService organizations,
            AccessPolicy policy,
            RoleAssignmentPolicy roles,
            AuditService audit) {
        this.memberships = memberships;
        this.organizations = organizations;
        this.policy = policy;
        this.roles = roles;
        this.audit = audit;
    }

    @Transactional
    public MembershipView changeRole(
            OrgContext context, UUID membershipId, OrgRole newRole, long expectedVersion) {
        OrgContext actor = lockAndRefresh(context);
        policy.require(actor, Permission.ROLE_ASSIGN);
        MembershipView target = activeTarget(actor, membershipId);

        if (target.userId().equals(actor.userId())) {
            throw new ForbiddenActionException(
                    "CANNOT_CHANGE_OWN_ROLE", "You cannot change your own role.");
        }
        // You may only touch roles you could grant yourself: no promoting above you, and no
        // changing someone who outranks you.
        if (!roles.canAssign(actor.role(), target.role())
                || !roles.canAssign(actor.role(), newRole)) {
            throw new AccessDeniedException(
                    actor.role() + " may not change " + target.role() + " to " + newRole);
        }
        if (target.version() != expectedVersion) {
            throw new ConflictException(
                    "STALE_VERSION",
                    "This member was changed by someone else. Reload and try again.");
        }
        if (target.role() == newRole) {
            return target;
        }
        if (target.role() == OrgRole.OWNER) {
            requireAnotherOwner(actor.organizationId());
        }

        MembershipView updated =
                memberships.changeRole(actor.organizationId(), membershipId, newRole);
        audit.record(
                AuditEvent.of(
                                AuditEventType.MEMBER_ROLE_CHANGED,
                                actor.userId(),
                                AuditTargetType.MEMBERSHIP,
                                membershipId)
                        .inOrganization(actor.organizationId())
                        .withMetadata(
                                Map.of(
                                        "fromRole", target.role().name(),
                                        "toRole", newRole.name())));
        return updated;
    }

    @Transactional
    public void remove(OrgContext context, UUID membershipId) {
        OrgContext actor = lockAndRefresh(context);
        policy.require(actor, Permission.MEMBER_REVOKE);
        MembershipView target = activeTarget(actor, membershipId);

        if (target.userId().equals(actor.userId())) {
            throw new ForbiddenActionException(
                    "CANNOT_REMOVE_SELF",
                    "You cannot remove yourself. Leave the organization instead.");
        }
        if (!roles.canAssign(actor.role(), target.role())) {
            throw new AccessDeniedException(actor.role() + " may not remove a " + target.role());
        }
        if (target.role() == OrgRole.OWNER) {
            requireAnotherOwner(actor.organizationId());
        }

        memberships.revoke(actor.organizationId(), membershipId);
        audit.record(
                AuditEvent.of(
                                AuditEventType.MEMBER_REMOVED,
                                actor.userId(),
                                AuditTargetType.MEMBERSHIP,
                                membershipId)
                        .inOrganization(actor.organizationId())
                        .withMetadata(Map.of("role", target.role().name())));
    }

    @Transactional
    public void leave(OrgContext context) {
        OrgContext actor = lockAndRefresh(context);
        if (actor.role() == OrgRole.OWNER) {
            requireAnotherOwner(actor.organizationId());
        }

        memberships.revoke(actor.organizationId(), actor.membershipId());
        audit.record(
                AuditEvent.of(
                                AuditEventType.MEMBER_LEFT,
                                actor.userId(),
                                AuditTargetType.MEMBERSHIP,
                                actor.membershipId())
                        .inOrganization(actor.organizationId())
                        .withMetadata(Map.of("role", actor.role().name())));
    }

    /** Locks the organization, then re-reads the caller's membership under the lock. */
    private OrgContext lockAndRefresh(OrgContext context) {
        organizations.lockForMembershipChanges(context.organizationId());
        MembershipView current =
                memberships
                        .findActiveMembership(context.organizationId(), context.userId())
                        .orElseThrow(NotFoundException::new);
        return new OrgContext(
                context.organizationId(),
                context.userId(),
                current.id(),
                current.role(),
                context.status());
    }

    private MembershipView activeTarget(OrgContext actor, UUID membershipId) {
        return memberships
                .find(actor.organizationId(), membershipId)
                .filter(member -> member.status() == MembershipStatus.ACTIVE)
                .orElseThrow(NotFoundException::new);
    }

    /** The invariant, in one place: an organization always keeps at least one active owner. */
    private void requireAnotherOwner(UUID organizationId) {
        if (memberships.countActiveByRole(organizationId, OrgRole.OWNER) <= 1) {
            throw new ConflictException(
                    "LAST_OWNER",
                    "An organization must keep at least one owner. Make someone else an owner first.");
        }
    }
}
