package com.l2c.nexus.support;

import com.l2c.nexus.identity.application.EmailVerificationService;
import com.l2c.nexus.identity.application.RecordingAccountEmails;
import com.l2c.nexus.identity.application.RegisterUserCommand;
import com.l2c.nexus.identity.application.RegistrationService;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Creates registered, email-verified accounts through the real services. */
@Component
public class VerifiedUserFactory {

    public static final String PASSWORD = "correct horse battery staple";

    private final RegistrationService registration;
    private final EmailVerificationService verification;
    private final RecordingAccountEmails emails;

    public VerifiedUserFactory(
            RegistrationService registration,
            EmailVerificationService verification,
            RecordingAccountEmails emails) {
        this.registration = registration;
        this.verification = verification;
        this.emails = emails;
    }

    /** Returns the new account's email address; its password is {@link #PASSWORD}. */
    public String create() {
        String email = "verified-" + UUID.randomUUID() + "@example.com";
        registration.register(new RegisterUserCommand(email, PASSWORD, "Verified"));
        verification.verify(emails.awaitNext(email).token());
        return email;
    }
}
