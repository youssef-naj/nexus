package com.l2c.nexus.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.l2c.nexus.TestcontainersConfiguration;
import com.l2c.nexus.identity.application.RecordingAccountEmails;
import com.l2c.nexus.identity.application.RegisterUserCommand;
import com.l2c.nexus.identity.application.RegistrationService;
import com.l2c.nexus.identity.application.TokenGenerator;
import com.l2c.nexus.identity.domain.TokenType;
import com.l2c.nexus.identity.domain.User;
import com.l2c.nexus.identity.domain.UserToken;
import com.l2c.nexus.identity.persistence.UserRepository;
import com.l2c.nexus.identity.persistence.UserTokenRepository;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class EmailVerificationEndpointIntegrationTest {

    private static final String STRONG = "correct horse battery staple";

    @Value("${local.server.port}")
    private int port;

    @Autowired private RegistrationService registration;
    @Autowired private UserRepository users;
    @Autowired private UserTokenRepository userTokens;
    @Autowired private TokenGenerator tokens;
    @Autowired private RecordingAccountEmails emails;

    private final CookieManager cookies = new CookieManager();
    private final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private String csrfToken() throws Exception {
        client.send(
                HttpRequest.newBuilder(uri("/api/system/ping")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        return cookies.getCookieStore().getCookies().stream()
                .filter(cookie -> cookie.getName().equals("XSRF-TOKEN"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No XSRF-TOKEN cookie was issued"))
                .getValue();
    }

    private HttpResponse<String> verify(String token, String csrfToken) throws Exception {
        HttpRequest.Builder request =
                HttpRequest.newBuilder(uri("/api/auth/verify-email"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"token\":\"" + token + "\"}"));
        if (csrfToken != null) {
            request.header("X-XSRF-TOKEN", csrfToken);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String uniqueEmail() {
        return "grace-" + UUID.randomUUID() + "@example.com";
    }

    private String registerAndGetToken(String email) {
        registration.register(new RegisterUserCommand(email, STRONG, "Grace"));
        return emails.awaitNext(email).token();
    }

    private User user(String email) {
        return users.findByEmailCaseInsensitive(email).orElseThrow();
    }

    @Test
    void verifiesTheEmailAndConsumesTheToken() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String csrf = csrfToken();
        assertThat(user(email).getEmailVerifiedAt()).isNull();

        assertThat(verify(token, csrf).statusCode()).isEqualTo(204);
        assertThat(user(email).getEmailVerifiedAt()).isNotNull();

        // The token is single-use
        assertThat(verify(token, csrf).statusCode()).isEqualTo(400);
    }

    @Test
    void storesOnlyTheHashOfTheToken() {
        String token = registerAndGetToken(uniqueEmail());

        assertThat(userTokens.findByTokenHash(token)).isEmpty();
        assertThat(userTokens.findByTokenHash(tokens.hash(token))).isPresent();
    }

    @Test
    void unknownTokenIsRejectedWithAGenericProblem() throws Exception {
        HttpResponse<String> response = verify("not-a-real-token", csrfToken());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type").orElse(""))
                .contains("application/problem+json");
    }

    @Test
    void expiredTokenIsRejectedAndDoesNotVerifyTheAccount() throws Exception {
        String email = uniqueEmail();
        registerAndGetToken(email); // drains the real email
        String raw = tokens.newToken();
        Instant now = Instant.now();
        userTokens.save(
                UserToken.issue(
                        user(email).getId(),
                        TokenType.VERIFY_EMAIL,
                        tokens.hash(raw),
                        now.minusSeconds(7200),
                        now.minusSeconds(3600)));

        assertThat(verify(raw, csrfToken()).statusCode()).isEqualTo(400);
        assertThat(user(email).getEmailVerifiedAt()).isNull();
    }

    @Test
    void reRegisteringAnUnverifiedAccountIssuesANewTokenAndInvalidatesTheOldOne() throws Exception {
        String email = uniqueEmail();
        String first = registerAndGetToken(email);
        registration.register(new RegisterUserCommand(email, STRONG, "Grace"));
        String second = emails.awaitNext(email).token();
        String csrf = csrfToken();

        assertThat(second).isNotEqualTo(first);
        assertThat(verify(first, csrf).statusCode()).isEqualTo(400);
        assertThat(verify(second, csrf).statusCode()).isEqualTo(204);
    }

    @Test
    void registeringAVerifiedAccountAgainSendsAnAlreadyRegisteredNotice() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        assertThat(verify(token, csrfToken()).statusCode()).isEqualTo(204);

        registration.register(new RegisterUserCommand(email, STRONG, "Grace"));

        assertThat(emails.awaitNext(email).kind())
                .isEqualTo(RecordingAccountEmails.Kind.ALREADY_REGISTERED);
    }

    @Test
    void verifyWithoutACsrfTokenIsForbidden() throws Exception {
        assertThat(verify("anything", null).statusCode()).isEqualTo(403);
    }
}
