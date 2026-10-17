package com.l2c.nexus.shared.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.l2c.nexus.TestcontainersConfiguration;
import com.l2c.nexus.identity.application.RecordingAccountEmails;
import com.l2c.nexus.support.HttpTestClient;
import com.l2c.nexus.support.LogCapture;
import com.l2c.nexus.support.VerifiedUserFactory;
import jakarta.persistence.EntityManager;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The logging policy (ADR-0033): logs never contain email addresses, passwords, tokens or request
 * contents. Real flows run, then everything that was logged is searched for the values used.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class LoggingPolicyIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired private RecordingAccountEmails accountEmails;
    @Autowired private VerifiedUserFactory users;
    @Autowired private EntityManager entityManager;
    @Autowired private TransactionTemplate transactions;

    private final Logger application = (Logger) LoggerFactory.getLogger("com.l2c.nexus");
    private Level previousLevel;

    @BeforeEach
    void logEverythingOurCodeSays() {
        previousLevel = application.getLevel();
        application.setLevel(Level.DEBUG);
    }

    @AfterEach
    void restoreLogLevel() {
        application.setLevel(previousLevel);
    }

    private static String registration(String email, String password) {
        return "{\"email\":\""
                + email
                + "\",\"password\":\""
                + password
                + "\",\"displayName\":\"Logger\"}";
    }

    private static void assertClientError(HttpResponse<String> response, String what) {
        assertThat(response.statusCode()).as(what).isBetween(400, 499);
    }

    @Test
    void registrationVerificationLoginAndLogoutLeaveNoPersonalDataInTheLogs() {
        String email = "logging-" + UUID.randomUUID() + "@example.com";
        String password = "Zq9-" + UUID.randomUUID();
        HttpTestClient http = new HttpTestClient(port);
        try (LogCapture logs = new LogCapture()) {
            assertThat(
                            http.postJson(
                                            "/api/auth/register",
                                            registration(email, password),
                                            http.csrfToken())
                                    .statusCode())
                    .isEqualTo(202);
            String token = accountEmails.awaitNext(email).token();
            assertThat(
                            http.postJson(
                                            "/api/auth/verify-email",
                                            "{\"token\":\"" + token + "\"}",
                                            http.csrfToken())
                                    .statusCode())
                    .isEqualTo(204);
            assertThat(
                            http.postForm(
                                            "/api/auth/login",
                                            Map.of("email", email, "password", password),
                                            http.csrfToken())
                                    .statusCode())
                    .isEqualTo(204);
            assertThat(http.get("/api/auth/me").statusCode()).isEqualTo(200);
            assertThat(
                            http.request("POST", "/api/auth/logout", null, http.csrfToken())
                                    .statusCode())
                    .isEqualTo(204);

            assertThat(logs.text())
                    .doesNotContain(email)
                    .doesNotContain(email.substring(0, email.indexOf('@')))
                    .doesNotContain(password)
                    .doesNotContain(token);
        }
    }

    @Test
    void failedAttemptsDuplicatesAndInvalidInputLeaveNoPersonalDataInTheLogs() {
        String suffix = UUID.randomUUID().toString();
        String existing = users.create();
        String unknown = "unknown-" + suffix + "@example.com";
        String unverified = "unverified-" + suffix + "@example.com";
        String wrongPassword = "wrong-password-" + suffix;
        String badToken = "bad-token-" + suffix;
        String malformedEmail = "malformed-" + suffix + "@example.com";
        String invalidEmail = "not-an-email-" + suffix;
        String shortPassword = "pw-" + suffix.substring(0, 4);
        HttpTestClient http = new HttpTestClient(port);
        try (LogCapture logs = new LogCapture()) {
            // The enumeration-safe paths: same answer whether or not the address is known
            assertThat(
                            http.postJson(
                                            "/api/auth/register",
                                            registration(existing, VerifiedUserFactory.PASSWORD),
                                            http.csrfToken())
                                    .statusCode())
                    .isEqualTo(202);
            assertThat(
                            http.postJson(
                                            "/api/auth/register",
                                            registration(unverified, VerifiedUserFactory.PASSWORD),
                                            http.csrfToken())
                                    .statusCode())
                    .isEqualTo(202);
            // Failed logins of every kind
            assertClientError(
                    http.postForm(
                            "/api/auth/login",
                            Map.of("email", unknown, "password", wrongPassword),
                            http.csrfToken()),
                    "unknown account");
            assertClientError(
                    http.postForm(
                            "/api/auth/login",
                            Map.of("email", existing, "password", wrongPassword),
                            http.csrfToken()),
                    "wrong password");
            assertClientError(
                    http.postForm(
                            "/api/auth/login",
                            Map.of("email", unverified, "password", VerifiedUserFactory.PASSWORD),
                            http.csrfToken()),
                    "unverified account");
            // Invalid tokens and invalid or malformed input
            assertClientError(
                    http.postJson(
                            "/api/auth/verify-email",
                            "{\"token\":\"" + badToken + "\"}",
                            http.csrfToken()),
                    "bad token");
            assertThat(
                            http.postJson(
                                            "/api/auth/register",
                                            registration(invalidEmail, shortPassword),
                                            http.csrfToken())
                                    .statusCode())
                    .isEqualTo(400);
            assertThat(
                            http.postJson(
                                            "/api/auth/register",
                                            "{\"email\":\"" + malformedEmail + "\",\"password\":",
                                            http.csrfToken())
                                    .statusCode())
                    .isEqualTo(400);

            assertThat(logs.text())
                    .doesNotContain(existing)
                    .doesNotContain(unknown)
                    .doesNotContain(unverified)
                    .doesNotContain(wrongPassword)
                    .doesNotContain(badToken)
                    .doesNotContain(malformedEmail)
                    .doesNotContain(invalidEmail)
                    .doesNotContain(shortPassword)
                    .doesNotContain(VerifiedUserFactory.PASSWORD);
        }
    }

    private void insertUserRow(String email) {
        transactions.executeWithoutResult(
                status ->
                        entityManager
                                .createNativeQuery(
                                        "INSERT INTO users (id, email, password_hash, display_name,"
                                                + " created_at, updated_at)"
                                                + " VALUES (?1, ?2, 'x', 'Duplicate', now(), now())")
                                .setParameter(1, UUID.randomUUID())
                                .setParameter(2, email)
                                .executeUpdate());
    }

    @Test
    void whenTheDatabaseItselfComplainsTheOffendingValueStillNeverReachesTheLogs() {
        String email = "duplicate-" + UUID.randomUUID() + "@example.com";
        insertUserRow(email);
        try (LogCapture logs = new LogCapture()) {
            // Goes through Hibernate and violates the unique index, so the database's own error
            // message (which contains the email) is produced and offered to the loggers
            assertThatThrownBy(() -> insertUserRow(email)).isInstanceOf(RuntimeException.class);

            assertThat(logs.text()).doesNotContain(email).doesNotContain("Key (");
        }
    }
}
