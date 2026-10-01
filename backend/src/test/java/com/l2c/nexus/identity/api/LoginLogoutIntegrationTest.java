package com.l2c.nexus.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.l2c.nexus.TestcontainersConfiguration;
import com.l2c.nexus.identity.application.EmailVerificationService;
import com.l2c.nexus.identity.application.RecordingAccountEmails;
import com.l2c.nexus.identity.application.RegisterUserCommand;
import com.l2c.nexus.identity.application.RegistrationService;
import com.l2c.nexus.identity.domain.User;
import com.l2c.nexus.identity.persistence.UserRepository;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class LoginLogoutIntegrationTest {

    private static final String STRONG = "correct horse battery staple";

    @Value("${local.server.port}")
    private int port;

    @Autowired private RegistrationService registration;
    @Autowired private EmailVerificationService verification;
    @Autowired private RecordingAccountEmails emails;
    @Autowired private UserRepository users;
    @Autowired private DataSource dataSource;

    private final CookieManager cookies = new CookieManager();
    private final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static String uniqueEmail() {
        return "login-" + UUID.randomUUID() + "@example.com";
    }

    private static String contentType(HttpResponse<String> response) {
        return response.headers().firstValue("Content-Type").orElse("");
    }

    private String csrfToken() throws Exception {
        HttpResponse<String> ping =
                client.send(
                        HttpRequest.newBuilder(uri("/api/system/ping")).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
        assertThat(ping.statusCode()).isEqualTo(200);
        return cookie("XSRF-TOKEN").orElseThrow(() -> new AssertionError("No CSRF cookie"));
    }

    private java.util.Optional<String> cookie(String name) {
        return cookies.getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals(name))
                .map(HttpCookie::getValue)
                .findFirst();
    }

    private HttpResponse<String> login(String email, String password, String csrf)
            throws Exception {
        String form =
                "email="
                        + URLEncoder.encode(email, StandardCharsets.UTF_8)
                        + "&password="
                        + URLEncoder.encode(password, StandardCharsets.UTF_8);
        HttpRequest.Builder request =
                HttpRequest.newBuilder(uri("/api/auth/login"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form));
        if (csrf != null) {
            request.header("X-XSRF-TOKEN", csrf);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> me() throws Exception {
        return client.send(
                HttpRequest.newBuilder(uri("/api/auth/me")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> logout(String csrf) throws Exception {
        return client.send(
                HttpRequest.newBuilder(uri("/api/auth/logout"))
                        .header("X-XSRF-TOKEN", csrf)
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private String registerAndVerify(String email) {
        registration.register(new RegisterUserCommand(email, STRONG, "Grace Hopper"));
        verification.verify(emails.awaitNext(email).token());
        return email;
    }

    private User user(String email) {
        return users.findByEmailCaseInsensitive(email).orElseThrow();
    }

    @Test
    void validCredentialsEstablishASessionThatMeRecognizes() throws Exception {
        String email = registerAndVerify(uniqueEmail());

        assertThat(login(email, STRONG, csrfToken()).statusCode()).isEqualTo(204);

        assertThat(cookie("SESSION")).isPresent();
        HttpResponse<String> me = me();
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(me.body()).contains(email).contains("Grace Hopper");
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSameResponse() throws Exception {
        String email = registerAndVerify(uniqueEmail());
        String csrf = csrfToken();

        HttpResponse<String> wrongPassword = login(email, "not the right password", csrf);
        HttpResponse<String> unknownEmail = login(uniqueEmail(), STRONG, csrf);

        assertThat(wrongPassword.statusCode()).isEqualTo(401);
        assertThat(unknownEmail.statusCode()).isEqualTo(401);
        assertThat(unknownEmail.body()).isEqualTo(wrongPassword.body());
        assertThat(contentType(wrongPassword)).contains("application/problem+json");
        assertThat(cookie("SESSION")).isEmpty();
    }

    @Test
    void anUnverifiedAccountIsOnlyRevealedAfterTheCorrectPassword() throws Exception {
        String email = uniqueEmail();
        registration.register(new RegisterUserCommand(email, STRONG, "Grace"));
        emails.awaitNext(email); // not verified
        String csrf = csrfToken();

        HttpResponse<String> correct = login(email, STRONG, csrf);
        HttpResponse<String> wrong = login(email, "not the right password", csrf);

        assertThat(correct.statusCode()).isEqualTo(403);
        assertThat(correct.body()).contains("EMAIL_NOT_VERIFIED");
        assertThat(wrong.statusCode()).isEqualTo(401);
        assertThat(cookie("SESSION")).isEmpty();
    }

    @Test
    void aDisabledAccountCannotLogInAndLooksLikeAnUnknownOne() throws Exception {
        String email = registerAndVerify(uniqueEmail());
        User user = user(email);
        user.disable(Instant.now());
        users.save(user);
        String csrf = csrfToken();

        HttpResponse<String> disabled = login(email, STRONG, csrf);
        HttpResponse<String> unknown = login(uniqueEmail(), STRONG, csrf);

        assertThat(disabled.statusCode()).isEqualTo(401);
        assertThat(disabled.body()).isEqualTo(unknown.body());
    }

    @Test
    void loginWithoutACsrfTokenIsForbidden() throws Exception {
        String email = registerAndVerify(uniqueEmail());

        HttpResponse<String> response = login(email, STRONG, null);

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(contentType(response)).contains("application/problem+json");
    }

    @Test
    void meRequiresAuthentication() throws Exception {
        HttpResponse<String> response = me();

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(contentType(response)).contains("application/problem+json");
    }

    @Test
    void logoutEndsTheSessionOnTheServerSoTheOldCookieStopsWorking() throws Exception {
        String email = registerAndVerify(uniqueEmail());
        login(email, STRONG, csrfToken());
        String oldSession = cookie("SESSION").orElseThrow();

        assertThat(logout(csrfToken()).statusCode()).isEqualTo(204);

        // Replay the old cookie from a client with no cookie jar: the server must refuse it.
        HttpResponse<String> replay =
                HttpClient.newHttpClient()
                        .send(
                                HttpRequest.newBuilder(uri("/api/auth/me"))
                                        .header("Cookie", "SESSION=" + oldSession)
                                        .GET()
                                        .build(),
                                HttpResponse.BodyHandlers.ofString());
        assertThat(replay.statusCode()).isEqualTo(401);
    }

    @Test
    void theSessionIsIndexedByUserIdAndNeverStoresPasswordMaterial() throws Exception {
        String email = registerAndVerify(uniqueEmail());
        UUID userId = user(email).getId();
        login(email, STRONG, csrfToken());

        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement ps =
                    c.prepareStatement(
                            "SELECT count(*) FROM spring_session WHERE principal_name = ?")) {
                ps.setString(1, userId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertThat(rs.getInt(1)).isEqualTo(1);
                }
            }
            try (PreparedStatement ps =
                    c.prepareStatement(
                            """
                                 SELECT a.attribute_bytes FROM spring_session_attributes a
                                 JOIN spring_session s ON s.primary_id = a.session_primary_id
                                 WHERE s.principal_name = ?
                                 """)) {
                ps.setString(1, userId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String stored = new String(rs.getBytes(1), StandardCharsets.ISO_8859_1);
                        assertThat(stored).doesNotContain("bcrypt", STRONG);
                    }
                }
            }
        }
    }

    @Test
    void anAccountDisabledAfterLoginLosesAccessAndItsSession() throws Exception {
        String email = registerAndVerify(uniqueEmail());
        login(email, STRONG, csrfToken());
        assertThat(me().statusCode()).isEqualTo(200);

        User user = user(email);
        user.disable(Instant.now());
        users.save(user);

        assertThat(me().statusCode()).isEqualTo(401);
    }
}
