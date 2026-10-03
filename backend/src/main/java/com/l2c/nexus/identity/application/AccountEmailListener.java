package com.l2c.nexus.identity.application;

import com.l2c.nexus.shared.ratelimit.RateLimitDecision;
import com.l2c.nexus.shared.ratelimit.RateLimiter;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Sends account emails after the HTTP response, on a separate thread, so the response time never
 * reveals which case occurred (ADR-0014). Each address receives a limited number of emails per
 * window; beyond that nothing is sent and nothing changes in the response (no enumeration).
 */
@Component
public class AccountEmailListener {

    private static final Logger log = LoggerFactory.getLogger(AccountEmailListener.class);

    private final EmailVerificationService verification;
    private final AccountEmails emails;
    private final RateLimiter limiter;
    private final AuthRateLimits rules;
    private final String publicBaseUrl;

    public AccountEmailListener(
            EmailVerificationService verification,
            AccountEmails emails,
            RateLimiter limiter,
            AuthRateLimits rules,
            @Value("${nexus.public-base-url}") String publicBaseUrl) {
        this.verification = verification;
        this.emails = emails;
        this.limiter = limiter;
        this.rules = rules;
        this.publicBaseUrl = publicBaseUrl;
    }

    @Async
    @EventListener
    public void on(VerificationEmailRequested event) {
        if (!withinEmailLimit(event.email())) {
            return;
        }
        try {
            String token = verification.issueFor(event.userId());
            String link = publicBaseUrl + "/verify-email?token=" + token;
            emails.sendVerificationEmail(event.email(), event.displayName(), link);
        } catch (RuntimeException e) {
            log.error("Could not send verification email for user {}", event.userId(), e);
        }
    }

    @Async
    @EventListener
    public void on(RegistrationAttemptedForExistingAccount event) {
        if (!withinEmailLimit(event.email())) {
            return;
        }
        try {
            emails.sendAlreadyRegisteredEmail(event.email());
        } catch (RuntimeException e) {
            log.error("Could not send the already-registered notice", e);
        }
    }

    private boolean withinEmailLimit(String email) {
        RateLimitDecision decision =
                limiter.tryAcquire(
                        "email:" + email.toLowerCase(Locale.ROOT), rules.emailPerAddress());
        if (!decision.allowed()) {
            // The address itself is not logged (personal data).
            log.warn("Account email suppressed: per-address limit reached");
        }
        return decision.allowed();
    }
}
