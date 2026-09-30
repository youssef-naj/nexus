package com.l2c.nexus.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.l2c.nexus.TestcontainersConfiguration;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class RegistrationEndpointIntegrationTest {

    private static final String STRONG = "correct horse battery staple";

    @Value("${local.server.port}")
    private int port;

    // JUnit creates a new instance per test, so every test starts with an empty cookie jar.
    private final CookieManager cookies = new CookieManager();
    private final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    /** Any request makes the server issue the XSRF-TOKEN cookie, exactly as a browser would. */
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

    private HttpResponse<String> register(String json, String csrfToken) throws Exception {
        HttpRequest.Builder request =
                HttpRequest.newBuilder(uri("/api/auth/register"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json));
        if (csrfToken != null) {
            request.header("X-XSRF-TOKEN", csrfToken);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String body(String email, String password, String displayName) {
        return """
                {"email":"%s","password":"%s","displayName":"%s"}
                """
                .formatted(email, password, displayName);
    }

    private static String uniqueEmail() {
        return "ada-" + UUID.randomUUID() + "@example.com";
    }

    private static String contentType(HttpResponse<String> response) {
        return response.headers().firstValue("Content-Type").orElse("");
    }

    @Test
    void registersWithAValidCsrfToken() throws Exception {
        HttpResponse<String> response =
                register(body(uniqueEmail(), STRONG, "Ada Lovelace"), csrfToken());

        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(response.body()).contains("Registration received");
    }

    @Test
    void postWithoutACsrfTokenIsForbiddenAsProblemDetails() throws Exception {
        HttpResponse<String> response = register(body(uniqueEmail(), STRONG, "Ada Lovelace"), null);

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(contentType(response)).contains("application/problem+json");
    }

    @Test
    void duplicateRegistrationIsIndistinguishableFromTheFirst() throws Exception {
        String email = uniqueEmail();
        String token = csrfToken();

        HttpResponse<String> first = register(body(email, STRONG, "Ada"), token);
        HttpResponse<String> second =
                register(body(email.toUpperCase(), "another long passphrase", "Eve"), token);

        assertThat(second.statusCode()).isEqualTo(first.statusCode()).isEqualTo(202);
        assertThat(second.body()).isEqualTo(first.body());
    }

    @Test
    void invalidFieldsReturnProblemDetailsWithFieldErrors() throws Exception {
        HttpResponse<String> response = register(body("not-an-email", STRONG, ""), csrfToken());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(contentType(response)).contains("application/problem+json");
        assertThat(response.body()).contains("\"errors\"", "\"email\"", "\"displayName\"");
    }

    @Test
    void weakPasswordReturnsAPasswordFieldError() throws Exception {
        HttpResponse<String> response = register(body(uniqueEmail(), "short", "Ada"), csrfToken());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("\"password\"", "at least 12");
    }

    @Test
    void malformedJsonReturns400AsProblemDetails() throws Exception {
        HttpResponse<String> response = register("{not json", csrfToken());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(contentType(response)).contains("application/problem+json");
    }
}
