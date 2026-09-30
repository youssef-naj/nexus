package com.l2c.nexus.identity.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Sends account emails after the HTTP response, on a separate thread, so the response time never
 * reveals which case occurred (ADR-0014). Failures are logged, not propagated.
 */
@Component
public class AccountEmailListener {

    private static final Logger log = LoggerFactory.getLogger(AccountEmailListener.class);

    private final EmailVerificationService verification;
    private final AccountEmails emails;
    private final String publicBaseUrl;

    public AccountEmailListener(
            EmailVerificationService verification,
            AccountEmails emails,
            @Value("${nexus.public-base-url}") String publicBaseUrl) {
        this.verification = verification;
        this.emails = emails;
        this.publicBaseUrl = publicBaseUrl;
    }

    @Async
    @EventListener
    public void on(VerificationEmailRequested event) {
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
        try {
            emails.sendAlreadyRegisteredEmail(event.email());
        } catch (RuntimeException e) {
            log.error("Could not send the already-registered notice", e);
        }
    }
}
