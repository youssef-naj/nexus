package com.l2c.nexus.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.l2c.nexus.TestcontainersConfiguration;
import com.l2c.nexus.identity.domain.User;
import com.l2c.nexus.identity.domain.UserStatus;
import com.l2c.nexus.identity.persistence.UserRepository;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RegistrationServiceIntegrationTest {

    private static final String STRONG = "correct horse battery staple";

    @Autowired private RegistrationService registration;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder passwordEncoder;

    private static String uniqueEmail() {
        return "Ada-" + UUID.randomUUID() + "@Example.com";
    }

    @Test
    void registersAUserWithAHashedPasswordAndNormalizedFields() {
        String email = uniqueEmail();

        RegistrationOutcome outcome =
                registration.register(
                        new RegisterUserCommand("  " + email + "  ", STRONG, "  Ada Lovelace "));

        assertThat(outcome).isEqualTo(RegistrationOutcome.CREATED);
        User user = users.findByEmailCaseInsensitive(email).orElseThrow();
        assertThat(user.getId()).isNotNull();
        assertThat(user.getEmail()).isEqualTo(email.toLowerCase(Locale.ROOT));
        assertThat(user.getDisplayName()).isEqualTo("Ada Lovelace");
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getEmailVerifiedAt()).isNull();
        assertThat(user.getPasswordHash()).isNotEqualTo(STRONG).startsWith("{bcrypt}");
        assertThat(passwordEncoder.matches(STRONG, user.getPasswordHash())).isTrue();
    }

    @Test
    void duplicateEmailIgnoringCaseIsReportedAndDoesNotOverwriteTheAccount() {
        String email = uniqueEmail();
        registration.register(new RegisterUserCommand(email, STRONG, "Ada"));

        RegistrationOutcome second =
                registration.register(
                        new RegisterUserCommand(
                                email.toUpperCase(Locale.ROOT),
                                "a completely different one",
                                "Eve"));

        assertThat(second).isEqualTo(RegistrationOutcome.ALREADY_REGISTERED);
        User user = users.findByEmailCaseInsensitive(email).orElseThrow();
        assertThat(user.getDisplayName()).isEqualTo("Ada");
        assertThat(passwordEncoder.matches(STRONG, user.getPasswordHash())).isTrue();
    }

    @Test
    void weakPasswordIsRejectedAndNoUserIsCreated() {
        String email = uniqueEmail();

        assertThatThrownBy(
                        () -> registration.register(new RegisterUserCommand(email, "short", "Ada")))
                .isInstanceOf(WeakPasswordException.class);

        assertThat(users.findByEmailCaseInsensitive(email)).isEmpty();
    }
}
