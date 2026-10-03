package com.l2c.nexus.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.l2c.nexus.TestcontainersConfiguration;
import com.l2c.nexus.support.HttpTestClient;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** Each test uses a different endpoint, so their per-IP counters never interfere. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "nexus.rate-limit.login-per-ip.limit=2",
            "nexus.rate-limit.register-per-ip.limit=3",
            "nexus.rate-limit.verify-per-ip.limit=2"
        })
@Import(TestcontainersConfiguration.class)
class AuthIpThrottlingIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    private HttpTestClient http;

    @BeforeEach
    void setUp() {
        http = new HttpTestClient(port);
    }

    private HttpResponse<String> register() {
        String json =
                """
                {"email":"%s","password":"correct horse battery staple","displayName":"Ada"}
                """
                        .formatted("ip-" + UUID.randomUUID() + "@example.com");
        return http.postJson("/api/auth/register", json, http.csrfToken());
    }

    @Test
    void theFourthRegistrationFromOneIpIsRejected() {
        assertThat(register().statusCode()).isEqualTo(202);
        assertThat(register().statusCode()).isEqualTo(202);
        assertThat(register().statusCode()).isEqualTo(202);

        HttpResponse<String> blocked = register();

        assertThat(blocked.statusCode()).isEqualTo(429);
        assertThat(HttpTestClient.retryAfterSeconds(blocked)).isPositive();
    }

    @Test
    void theThirdVerificationAttemptFromOneIpIsRejected() {
        String body = "{\"token\":\"not-a-real-token\"}";
        assertThat(http.postJson("/api/auth/verify-email", body, http.csrfToken()).statusCode())
                .isEqualTo(400);
        assertThat(http.postJson("/api/auth/verify-email", body, http.csrfToken()).statusCode())
                .isEqualTo(400);

        assertThat(http.postJson("/api/auth/verify-email", body, http.csrfToken()).statusCode())
                .isEqualTo(429);
    }

    @Test
    void theThirdLoginAttemptFromOneIpIsRejectedWhateverTheEmail() {
        for (int i = 0; i < 2; i++) {
            HttpResponse<String> attempt =
                    http.postForm(
                            "/api/auth/login",
                            Map.of("email", "nobody-" + i + "@example.com", "password", "x"),
                            http.csrfToken());
            assertThat(attempt.statusCode()).isEqualTo(401);
        }

        HttpResponse<String> blocked =
                http.postForm(
                        "/api/auth/login",
                        Map.of("email", "someone-else@example.com", "password", "x"),
                        http.csrfToken());

        assertThat(blocked.statusCode()).isEqualTo(429);
    }
}
