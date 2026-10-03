package com.l2c.nexus.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.l2c.nexus.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(properties = "nexus.rate-limit.email-per-address.limit=1")
@Import(TestcontainersConfiguration.class)
class EmailSendThrottleIntegrationTest {

    private static final String STRONG = "correct horse battery staple";

    @Autowired private RegistrationService registration;
    @Autowired private RecordingAccountEmails emails;

    private static String uniqueEmail() {
        return "mailbomb-" + UUID.randomUUID() + "@example.com";
    }

    @Test
    void onlyOneEmailPerAddressIsSentWithinTheWindow() {
        String email = uniqueEmail();

        registration.register(new RegisterUserCommand(email, STRONG, "Ada"));
        assertThat(emails.awaitNext(email).kind())
                .isEqualTo(RecordingAccountEmails.Kind.VERIFICATION);

        // The account is still unverified, so this would normally send a fresh link
        RegistrationOutcome second =
                registration.register(new RegisterUserCommand(email, STRONG, "Ada"));

        // The caller sees the usual outcome, but no second email goes out
        assertThat(second).isEqualTo(RegistrationOutcome.ALREADY_REGISTERED);
        assertThat(emails.pollWithin(email, 1500)).isNull();
    }

    @Test
    void otherAddressesAreNotAffected() {
        String first = uniqueEmail();
        String second = uniqueEmail();

        registration.register(new RegisterUserCommand(first, STRONG, "Ada"));
        registration.register(new RegisterUserCommand(second, STRONG, "Eve"));

        assertThat(emails.awaitNext(first)).isNotNull();
        assertThat(emails.awaitNext(second)).isNotNull();
    }
}
