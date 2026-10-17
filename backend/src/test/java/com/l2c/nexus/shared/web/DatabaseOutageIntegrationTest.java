package com.l2c.nexus.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.l2c.nexus.TestcontainersConfiguration;
import com.l2c.nexus.support.DatabaseOutageTestConfiguration;
import com.l2c.nexus.support.HttpTestClient;
import com.l2c.nexus.support.LogCapture;
import com.l2c.nexus.support.PlatformFixtures;
import com.l2c.nexus.support.PlatformFixtures.Account;
import com.l2c.nexus.support.VerifiedUserFactory;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** What users and orchestrators see while the database is unreachable (ADR-0033). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, DatabaseOutageTestConfiguration.class})
class DatabaseOutageIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired private PlatformFixtures fixtures;

    @AfterEach
    void restoreTheDatabase() {
        DatabaseOutageTestConfiguration.databaseUp();
    }

    private static String incidentOf(String body) {
        Matcher matcher = Pattern.compile("\"incidentId\":\"([0-9a-f]{8})\"").matcher(body);
        assertThat(matcher.find()).as("an incident reference in " + body).isTrue();
        return matcher.group(1);
    }

    @Test
    void endpointsThatDoNotNeedTheDatabaseKeepAnswering() {
        DatabaseOutageTestConfiguration.databaseDown();

        HttpResponse<String> ping = new HttpTestClient(port).get("/api/system/ping");

        assertThat(ping.statusCode()).isEqualTo(200);
    }

    @Test
    void aSignedInUserGetsA503WithRetryAfterAndNotA401WhileTheDatabaseIsDown() {
        Account user = fixtures.signedIn(port, false);
        try (LogCapture logs = new LogCapture()) {
            DatabaseOutageTestConfiguration.databaseDown();

            HttpResponse<String> response = user.http().get("/api/auth/me");

            assertThat(response.statusCode()).isEqualTo(503);
            assertThat(response.headers().firstValue("Retry-After")).contains("5");
            assertThat(HttpTestClient.contentType(response)).contains("application/problem+json");
            assertThat(response.headers().firstValue("Content-Security-Policy")).isPresent();
            assertThat(response.headers().firstValue("Referrer-Policy")).contains("no-referrer");
            assertThat(response.body()).doesNotContain(user.email());
            String incident = incidentOf(response.body());
            assertThat(logs.text()).contains("Service unavailable, incident " + incident);
        }
    }

    @Test
    void aLoginDuringAnOutageIsUnavailableNotAWrongPasswordAndLeavesNoFailedAttempt() {
        Account user = fixtures.signedIn(port, false);
        HttpTestClient http = new HttpTestClient(port);
        Map<String, String> credentials =
                Map.of("email", user.email(), "password", VerifiedUserFactory.PASSWORD);

        String csrf = http.csrfToken();
        DatabaseOutageTestConfiguration.databaseDown();
        HttpResponse<String> during = http.postForm("/api/auth/login", credentials, csrf);
        DatabaseOutageTestConfiguration.databaseUp();
        HttpResponse<String> after =
                http.postForm("/api/auth/login", credentials, http.csrfToken());

        assertThat(during.statusCode())
                .as("an outage must not look like a wrong password")
                .isEqualTo(503);
        assertThat(after.statusCode()).isEqualTo(204);
    }

    @Test
    void aRegistrationDuringAnOutageIsUnavailableAndLeavesNothingHalfSaved() {
        String email = "outage-" + UUID.randomUUID() + "@example.com";
        String body =
                "{\"email\":\""
                        + email
                        + "\",\"password\":\""
                        + VerifiedUserFactory.PASSWORD
                        + "\",\"displayName\":\"Outage\"}";
        HttpTestClient http = new HttpTestClient(port);

        String csrf = http.csrfToken();
        DatabaseOutageTestConfiguration.databaseDown();
        HttpResponse<String> during = http.postJson("/api/auth/register", body, csrf);
        DatabaseOutageTestConfiguration.databaseUp();
        HttpResponse<String> after = http.postJson("/api/auth/register", body, http.csrfToken());

        assertThat(during.statusCode()).isEqualTo(503);
        assertThat(during.headers().firstValue("Retry-After")).contains("5");
        assertThat(during.body()).doesNotContain(email);
        assertThat(after.statusCode())
                .as("the same registration works once the database is back")
                .isEqualTo(202);
    }

    @Test
    void readinessFollowsTheDatabaseButLivenessDoesNot() {
        HttpTestClient http = new HttpTestClient(port);
        assertThat(http.get("/api/actuator/health/readiness").statusCode()).isEqualTo(200);

        DatabaseOutageTestConfiguration.databaseDown();
        HttpResponse<String> readiness = http.get("/api/actuator/health/readiness");
        HttpResponse<String> liveness = http.get("/api/actuator/health/liveness");

        assertThat(readiness.statusCode()).as("stop sending traffic").isEqualTo(503);
        assertThat(readiness.body()).contains("DOWN");
        assertThat(liveness.statusCode()).as("but do not restart a healthy process").isEqualTo(200);
        assertThat(liveness.body()).contains("UP");
    }

    @Test
    void theServiceRecoversWithoutARestartAndTheSessionSurvivesTheOutage() {
        Account user = fixtures.signedIn(port, false);
        DatabaseOutageTestConfiguration.databaseDown();
        assertThat(user.http().get("/api/auth/me").statusCode()).isEqualTo(503);

        DatabaseOutageTestConfiguration.databaseUp();
        HttpResponse<String> recovered = user.http().get("/api/auth/me");

        assertThat(recovered.statusCode()).isEqualTo(200);
        assertThat(recovered.body()).contains(user.email());
    }
}
