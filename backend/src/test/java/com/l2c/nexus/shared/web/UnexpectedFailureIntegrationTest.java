package com.l2c.nexus.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.l2c.nexus.TestcontainersConfiguration;
import com.l2c.nexus.support.FailureTestController;
import com.l2c.nexus.support.HttpTestClient;
import com.l2c.nexus.support.LogCapture;
import com.l2c.nexus.support.PlatformFixtures;
import com.l2c.nexus.support.PlatformFixtures.Account;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** An unexpected failure gives the user a generic answer and the log a safe, correlated line. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class UnexpectedFailureIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired private PlatformFixtures fixtures;

    private Account user;

    @BeforeEach
    void signIn() {
        user = fixtures.signedIn(port, false);
    }

    private static String incidentOf(String body) {
        Matcher matcher = Pattern.compile("\"incidentId\":\"([0-9a-f]{8})\"").matcher(body);
        assertThat(matcher.find()).as("an incident reference in " + body).isTrue();
        return matcher.group(1);
    }

    @Test
    void anUnexpectedFailureBecomesAGenericResponseWithAnIncidentReferenceAndTheSecurityHeaders() {
        HttpResponse<String> response = user.http().get("/api/test-support/fail");

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(HttpTestClient.contentType(response)).contains("application/problem+json");
        assertThat(response.body())
                .contains("An unexpected error occurred.")
                .doesNotContain(FailureTestController.SECRET)
                .doesNotContain("IllegalStateException")
                .doesNotContain("FailureTestController");
        assertThat(incidentOf(response.body())).hasSize(8);
        assertThat(response.headers().firstValue("Content-Security-Policy")).isPresent();
        assertThat(response.headers().firstValue("Cache-Control")).isPresent();
    }

    @Test
    void theLogCarriesTheSameIncidentReferenceAndTheExceptionTypeButNeverItsMessage() {
        try (LogCapture logs = new LogCapture()) {
            HttpResponse<String> response = user.http().get("/api/test-support/fail");

            String incident = incidentOf(response.body());
            assertThat(logs.text())
                    .contains("Unhandled failure, incident " + incident)
                    .contains("java.lang.IllegalStateException")
                    .doesNotContain(FailureTestController.SECRET);
        }
    }

    @Test
    void databaseErrorDetailsNeverReachTheClientOrTheLogs() {
        try (LogCapture logs = new LogCapture()) {
            HttpResponse<String> response = user.http().get("/api/test-support/db-detail");

            assertThat(response.statusCode()).isEqualTo(500);
            assertThat(response.body())
                    .doesNotContain(FailureTestController.LEAKED_EMAIL)
                    .doesNotContain("uq_users_email_lower")
                    .doesNotContain("duplicate key");
            assertThat(logs.text())
                    .contains("DataIntegrityViolationException")
                    .doesNotContain(FailureTestController.LEAKED_EMAIL)
                    .doesNotContain("uq_users_email_lower")
                    .doesNotContain("duplicate key");
        }
    }

    @Test
    void everyIncidentHasItsOwnReference() {
        String first = incidentOf(user.http().get("/api/test-support/fail").body());
        String second = incidentOf(user.http().get("/api/test-support/fail").body());

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void malformedInputIsAClientErrorNotAnIncident() {
        HttpTestClient http = new HttpTestClient(port);
        try (LogCapture logs = new LogCapture()) {
            HttpResponse<String> response =
                    http.postJson("/api/auth/register", "{\"email\":", http.csrfToken());

            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(response.body()).doesNotContain("incidentId");
            assertThat(logs.text()).doesNotContain("Unhandled failure");
        }
    }

    @Test
    void unknownRoutesAreNotIncidentsEither() {
        try (LogCapture logs = new LogCapture()) {
            HttpResponse<String> response =
                    user.http().get("/api/orgs/" + UUID.randomUUID() + "/nothing-here");

            assertThat(response.statusCode()).isEqualTo(404);
            assertThat(logs.text()).doesNotContain("Unhandled failure");
        }
    }
}
