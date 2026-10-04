package com.l2c.nexus.team.application;

import com.l2c.nexus.audit.application.AuditEvent;
import com.l2c.nexus.audit.application.AuditEventType;
import com.l2c.nexus.audit.application.AuditService;
import com.l2c.nexus.audit.application.AuditTargetType;
import com.l2c.nexus.identity.application.UserDirectory;
import com.l2c.nexus.identity.application.UserSummary;
import com.l2c.nexus.membership.application.MembershipService;
import com.l2c.nexus.membership.application.RoleAssignmentPolicy;
import com.l2c.nexus.membership.domain.OrgRole;
import com.l2c.nexus.organization.application.AccessPolicy;
import com.l2c.nexus.organization.application.OrgContext;
import com.l2c.nexus.organization.application.OrganizationService;
import com.l2c.nexus.organization.application.Permission;
import com.l2c.nexus.shared.error.ConflictException;
import com.l2c.nexus.shared.error.NotFoundException;
import com.l2c.nexus.shared.ratelimit.RateLimitDecision;
import com.l2c.nexus.shared.ratelimit.RateLimitExceededException;
import com.l2c.nexus.shared.ratelimit.RateLimiter;
import com.l2c.nexus.shared.security.TokenGenerator;
import com.l2c.nexus.team.domain.Invitation;
import com.l2c.nexus.team.domain.InvitationStatus;
import com.l2c.nexus.team.persistence.InvitationRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvitationService {

    static final Duration LIFETIME = Duration.ofDays(7);

    private final InvitationRepository invitations;
    private final MembershipService memberships;
    private final UserDirectory users;
    private final OrganizationService organizations;
    private final AccessPolicy policy;
    private final RoleAssignmentPolicy roles;
    private final AuditService audit;
    private final TokenGenerator tokens;
    private final RateLimiter limiter;
    private final TeamRateLimits limits;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public InvitationService(
            InvitationRepository invitations,
            MembershipService memberships,
            UserDirectory users,
            OrganizationService organizations,
            AccessPolicy policy,
            RoleAssignmentPolicy roles,
            AuditService audit,
            TokenGenerator tokens,
            RateLimiter limiter,
            TeamRateLimits limits,
            ApplicationEventPublisher events,
            Clock clock) {
        this.invitations = invitations;
        this.memberships = memberships;
        this.users = users;
        this.organizations = organizations;
        this.policy = policy;
        this.roles = roles;
        this.audit = audit;
        this.tokens = tokens;
        this.limiter = limiter;
        this.limits = limits;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public InvitationView create(OrgContext org, String rawEmail, OrgRole role) {
        policy.require(org, Permission.MEMBER_INVITE);
        requireCanGrant(org, role);
        enforceRateLimit(org.organizationId());

        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        users.findByEmail(email)
                .flatMap(user -> memberships.findActiveMembership(org.organizationId(), user.id()))
                .ifPresent(
                        member -> {
                            throw new ConflictException(
                                    "ALREADY_MEMBER", "This person is already a member.");
                        });

        Instant now = clock.instant();
        supersedePending(org, email, now);

        String rawToken = tokens.newToken();
        Invitation saved =
                invitations.save(
                        Invitation.create(
                                org.organizationId(),
                                email,
                                role,
                                tokens.hash(rawToken),
                                org.membershipId(),
                                now,
                                now.plus(LIFETIME)));
        audit.record(
                AuditEvent.of(
                                AuditEventType.INVITATION_CREATED,
                                org.userId(),
                                AuditTargetType.INVITATION,
                                saved.getId())
                        .inOrganization(org.organizationId())
                        .withMetadata(Map.of("role", role.name())));

        String inviterName =
                users.findActiveById(org.userId())
                        .map(UserSummary::displayName)
                        .orElse("A teammate");
        String organizationName = organizations.info(org.organizationId()).name();
        // Delivered only after this transaction commits (see InvitationEmailListener)
        events.publishEvent(
                new InvitationCreated(email, organizationName, inviterName, role, rawToken));
        return InvitationView.from(saved, now);
    }

    @Transactional(readOnly = true)
    public List<InvitationView> listPending(OrgContext org) {
        policy.require(org, Permission.MEMBER_INVITE);
        Instant now = clock.instant();
        return invitations
                .findByOrganizationIdAndStatusOrderByCreatedAtDesc(
                        org.organizationId(), InvitationStatus.PENDING)
                .stream()
                .map(invitation -> InvitationView.from(invitation, now))
                .toList();
    }

    @Transactional
    public void revoke(OrgContext org, UUID invitationId) {
        policy.require(org, Permission.MEMBER_INVITE);
        // Scoped by organization: an id belonging to another tenant is simply "not found"
        Invitation invitation =
                invitations
                        .findByIdAndOrganizationId(invitationId, org.organizationId())
                        .orElseThrow(NotFoundException::new);
        requireCanGrant(org, invitation.getRole());

        Instant now = clock.instant();
        if (invitations.transition(
                        invitation.getId(), InvitationStatus.PENDING, InvitationStatus.REVOKED, now)
                != 1) {
            throw new NotFoundException(); // already decided
        }
        recordRevoked(org, invitation);
    }

    /** An administrator may only touch invitations for roles they could grant themselves. */
    private void requireCanGrant(OrgContext org, OrgRole role) {
        if (!roles.canAssign(org.role(), role)) {
            throw new AccessDeniedException(org.role() + " may not grant the role " + role);
        }
    }

    private void supersedePending(OrgContext org, String email, Instant now) {
        for (Invitation old :
                invitations.findByOrganizationIdAndEmailAndStatus(
                        org.organizationId(), email, InvitationStatus.PENDING)) {
            requireCanGrant(org, old.getRole());
            if (invitations.transition(
                            old.getId(), InvitationStatus.PENDING, InvitationStatus.REVOKED, now)
                    == 1) {
                recordRevoked(org, old);
            }
        }
    }

    private void recordRevoked(OrgContext org, Invitation invitation) {
        audit.record(
                AuditEvent.of(
                                AuditEventType.INVITATION_REVOKED,
                                org.userId(),
                                AuditTargetType.INVITATION,
                                invitation.getId())
                        .inOrganization(org.organizationId())
                        .withMetadata(Map.of("role", invitation.getRole().name())));
    }

    private void enforceRateLimit(UUID organizationId) {
        RateLimitDecision decision =
                limiter.tryAcquire("invite:org:" + organizationId, limits.invitePerOrg());
        if (!decision.allowed()) {
            throw new RateLimitExceededException(decision.retryAfterSeconds());
        }
    }
}
