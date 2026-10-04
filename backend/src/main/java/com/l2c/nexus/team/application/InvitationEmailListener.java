package com.l2c.nexus.team.application;

import com.l2c.nexus.shared.ratelimit.RateLimitDecision;
import com.l2c.nexus.shared.ratelimit.RateLimiter;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends the invitation email only AFTER the transaction has committed, so an email never refers to
 * an invitation that was rolled back. Runs on a separate thread. The per-address budget is shared
 * with account emails (same limiter key).
 */
@Component
public class InvitationEmailListener {

    private static final Logger log = LoggerFactory.getLogger(InvitationEmailListener.class);

    private final InvitationEmails emails;
    private final RateLimiter limiter;
    private final TeamRateLimits limits;
    private final String publicBaseUrl;

    public InvitationEmailListener(
            InvitationEmails emails,
            RateLimiter limiter,
            TeamRateLimits limits,
            @Value("${nexus.public-base-url}") String publicBaseUrl) {
        this.emails = emails;
        this.limiter = limiter;
        this.limits = limits;
        this.publicBaseUrl = publicBaseUrl;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(InvitationCreated event) {
        RateLimitDecision decision =
                limiter.tryAcquire(
                        "email:" + event.email().toLowerCase(Locale.ROOT),
                        limits.emailPerAddress());
        if (!decision.allowed()) {
            log.warn("Invitation email suppressed: per-address limit reached");
            return;
        }
        try {
            emails.sendInvitation(
                    event.email(),
                    event.organizationName(),
                    event.inviterName(),
                    event.role().name(),
                    publicBaseUrl + "/invitations/accept?token=" + event.rawToken());
        } catch (RuntimeException e) {
            log.error("Could not send an invitation email", e);
        }
    }
}
