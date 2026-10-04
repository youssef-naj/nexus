package com.l2c.nexus.team.application;

import com.l2c.nexus.audit.application.AuditEvent;
import com.l2c.nexus.audit.application.AuditEventType;
import com.l2c.nexus.audit.application.AuditService;
import com.l2c.nexus.audit.application.AuditTargetType;
import com.l2c.nexus.identity.application.UserDirectory;
import com.l2c.nexus.identity.application.UserSummary;
import com.l2c.nexus.membership.application.MembershipService;
import com.l2c.nexus.organization.application.OrganizationInfo;
import com.l2c.nexus.organization.application.OrganizationService;
import com.l2c.nexus.organization.domain.OrganizationStatus;
import com.l2c.nexus.shared.security.TokenGenerator;
import com.l2c.nexus.team.domain.Invitation;
import com.l2c.nexus.team.domain.InvitationStatus;
import com.l2c.nexus.team.persistence.InvitationRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * An invitation is usable only by a signed-in, verified account whose email equals the invited
 * address AND that holds the token. Every other case fails with the same generic error.
 */
@Service
public class InvitationAcceptanceService {

    private final InvitationRepository invitations;
    private final MembershipService memberships;
    private final UserDirectory users;
    private final OrganizationService organizations;
    private final AuditService audit;
    private final TokenGenerator tokens;
    private final Clock clock;

    public InvitationAcceptanceService(
            InvitationRepository invitations,
            MembershipService memberships,
            UserDirectory users,
            OrganizationService organizations,
            AuditService audit,
            TokenGenerator tokens,
            Clock clock) {
        this.invitations = invitations;
        this.memberships = memberships;
        this.users = users;
        this.organizations = organizations;
        this.audit = audit;
        this.tokens = tokens;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public InvitationPreview preview(UUID userId, String rawToken) {
        Invitation invitation = usable(userId, rawToken, clock.instant());
        return new InvitationPreview(
                organizations.info(invitation.getOrganizationId()).name(), invitation.getRole());
    }

    @Transactional
    public AcceptedInvitation accept(UUID userId, String rawToken) {
        Instant now = clock.instant();
        Invitation invitation = usable(userId, rawToken, now);

        // One atomic statement decides the winner: pending and unexpired, or nothing
        if (invitations.transitionIfUnexpired(
                        invitation.getId(),
                        InvitationStatus.PENDING,
                        InvitationStatus.ACCEPTED,
                        now)
                != 1) {
            throw new InvalidInvitationException();
        }
        memberships.addOrReactivate(invitation.getOrganizationId(), userId, invitation.getRole());
        record(AuditEventType.INVITATION_ACCEPTED, userId, invitation);

        OrganizationInfo org = organizations.info(invitation.getOrganizationId());
        return new AcceptedInvitation(org.id(), org.name(), invitation.getRole());
    }

    @Transactional
    public void reject(UUID userId, String rawToken) {
        Instant now = clock.instant();
        Invitation invitation = usable(userId, rawToken, now);
        if (invitations.transition(
                        invitation.getId(),
                        InvitationStatus.PENDING,
                        InvitationStatus.REJECTED,
                        now)
                != 1) {
            throw new InvalidInvitationException();
        }
        record(AuditEventType.INVITATION_REJECTED, userId, invitation);
    }

    private Invitation usable(UUID userId, String rawToken, Instant now) {
        UserSummary user =
                users.findActiveById(userId).orElseThrow(InvalidInvitationException::new);
        Invitation invitation =
                invitations
                        .findByTokenHash(tokens.hash(rawToken))
                        .orElseThrow(InvalidInvitationException::new);

        boolean usable =
                invitation.getStatus() == InvitationStatus.PENDING
                        && invitation.getExpiresAt().isAfter(now)
                        && invitation.getEmail().equals(user.email().toLowerCase(Locale.ROOT))
                        && memberships
                                .findActiveMembership(invitation.getOrganizationId(), userId)
                                .isEmpty()
                        && organizations.info(invitation.getOrganizationId()).status()
                                == OrganizationStatus.ACTIVE;
        if (!usable) {
            throw new InvalidInvitationException();
        }
        return invitation;
    }

    private void record(AuditEventType type, UUID actor, Invitation invitation) {
        audit.record(
                AuditEvent.of(type, actor, AuditTargetType.INVITATION, invitation.getId())
                        .inOrganization(invitation.getOrganizationId())
                        .withMetadata(Map.of("role", invitation.getRole().name())));
    }
}
