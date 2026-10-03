package com.l2c.nexus.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.l2c.nexus.TestcontainersConfiguration;
import com.l2c.nexus.identity.application.EmailVerificationService;
import com.l2c.nexus.identity.application.RecordingAccountEmails;
import com.l2c.nexus.identity.application.RegisterUserCommand;
import com.l2c.nexus.identity.application.RegistrationService;
import com.l2c.nexus.support.HttpTestClient;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "nexus.rate-limit.login-failures-per-account.limit=3")
@Import(TestcontainersConfiguration.class)
class LoginThrottlingIntegrationTest {

    private static final String STRONG = "correct horse battery staple";
    private static final String WRONG = "definitely not the password";

    @Value("${local.server.port}")
    private int port;

    @Autowired private RegistrationService registration;
    @Autowired private EmailVerificationService verification;
    @Autowired private RecordingAccountEmails emails;

    private HttpTestClient http;

    @BeforeEach
    void setUp() {
        http = new HttpTestClient(port);
    }

    private String verifiedUser() {
        String email = "throttle-" + UUID.randomUUID() + "@example.com";
        registration.register(new RegisterUserCommand(email, STRONG, "Grace"));
        verification.verify(emails.awaitNext(email).token());
        return email;
    }

    private HttpResponse<String> login(String email, String password) {
        return http.postForm(
                "/api/auth/login", Map.of("email", email, "password", password), http.csrfToken());
    }

    @Test
    void theFourthAttemptIsBlockedEvenWithTheCorrectPassword() {
        String email = verifiedUser();
        for (int i = 0; i < 3; i++) {
            assertThat(login(email, WRONG).statusCode()).isEqualTo(401);
        }

        HttpResponse<String> blocked = login(email, STRONG);

        assertThat(blocked.statusCode()).isEqualTo(429);
        assertThat(HttpTestClient.contentType(blocked)).contains("application/problem+json");
        assertThat(HttpTestClient.retryAfterSeconds(blocked)).isBetween(1L, 60L);
    }

    @Test
    void otherAccountsAreNotAffected() {
        String blockedEmail = verifiedUser();
        String otherEmail = verifiedUser();
        for (int i = 0; i < 3; i++) {
            login(blockedEmail, WRONG);
        }

        assertThat(login(blockedEmail, STRONG).statusCode()).isEqualTo(429);
        assertThat(login(otherEmail, STRONG).statusCode()).isEqualTo(204);
    }

    @Test
    void aSuccessfulLoginClearsTheFailureCount() {
        String email = verifiedUser();

        assertThat(login(email, WRONG).statusCode()).isEqualTo(401);
        assertThat(login(email, WRONG).statusCode()).isEqualTo(401);
        assertThat(login(email, STRONG).statusCode()).isEqualTo(204);
        // Two more failures are fine because the count restarted from zero
        assertThat(login(email, WRONG).statusCode()).isEqualTo(401);
        assertThat(login(email, WRONG).statusCode()).isEqualTo(401);
        assertThat(login(email, STRONG).statusCode()).isEqualTo(204);
    }
}
