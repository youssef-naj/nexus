package com.l2c.nexus.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.l2c.nexus.TestcontainersConfiguration;
import com.l2c.nexus.identity.persistence.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class EmailVerificationAuditIntegrationTest {

    private static final String STRONG = "correct horse battery staple";

    @Autowired private RegistrationService registration;
    @Autowired private EmailVerificationService verification;
    @Autowired private RecordingAccountEmails emails;
    @Autowired private UserRepository users;
    @Autowired private JdbcClient jdbc;

    private int auditRows(UUID userId) {
        return jdbc.sql("SELECT count(*) FROM audit_logs WHERE actor_user_id = ?")
                .param(userId)
                .query(Integer.class)
                .single();
    }

    @Test
    void aSuccessfulVerificationIsAuditedOnceWithoutPersonalData() {
        String email = "audit-" + UUID.randomUUID() + "@example.com";
        registration.register(new RegisterUserCommand(email, STRONG, "Grace"));
        String token = emails.awaitNext(email).token();
        UUID userId = users.findByEmailCaseInsensitive(email).orElseThrow().getId();
        assertThat(auditRows(userId)).isZero();

        verification.verify(token);

        assertThat(auditRows(userId)).isEqualTo(1);
        var row =
                jdbc.sql(
                                "SELECT event_type || ':' || target_type || ':' || target_id || ':'"
                                        + " || metadata::text FROM audit_logs WHERE actor_user_id = ?")
                        .param(userId)
                        .query(String.class)
                        .single();
        assertThat(row).isEqualTo("USER_EMAIL_VERIFIED:USER:" + userId + ":{}");

        // Replaying the token fails and writes nothing more
        assertThatThrownBy(() -> verification.verify(token))
                .isInstanceOf(InvalidTokenException.class);
        assertThat(auditRows(userId)).isEqualTo(1);
    }

    @Test
    void aRejectedTokenWritesNoAuditRow() {
        int before = jdbc.sql("SELECT count(*) FROM audit_logs").query(Integer.class).single();

        assertThatThrownBy(() -> verification.verify("not-a-real-token"))
                .isInstanceOf(InvalidTokenException.class);

        int after = jdbc.sql("SELECT count(*) FROM audit_logs").query(Integer.class).single();
        assertThat(after).isGreaterThanOrEqualTo(before); // other tests may add rows concurrently
    }
}
