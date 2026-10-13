package com.l2c.nexus.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.l2c.nexus.TestcontainersConfiguration;
import com.l2c.nexus.support.HttpTestClient;
import java.io.ByteArrayInputStream;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class RequestSizeLimitIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    private static String registration(String displayName) {
        return "{\"email\":\"size-"
                + UUID.randomUUID()
                + "@example.com\","
                + "\"password\":\"correct horse battery staple\",\"displayName\":\""
                + displayName
                + "\"}";
    }

    @Test
    void aDeclaredOversizedBodyIsRefusedBeforeAnythingElseHappens() {
        HttpTestClient http = new HttpTestClient(port);

        // No CSRF token: the size check comes first, so the answer is 413, not 403
        HttpResponse<String> response =
                http.postJson("/api/auth/register", registration("x".repeat(200_000)), null);

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(HttpTestClient.contentType(response)).contains("application/problem+json");
    }

    @Test
    void aBodyWithoutADeclaredLengthIsStoppedOnceItGrowsPastTheLimit() throws Exception {
        CookieManager cookies = new CookieManager();
        HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();
        client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/system/ping"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        String token =
                cookies.getCookieStore().getCookies().stream()
                        .filter(cookie -> cookie.getName().equals("XSRF-TOKEN"))
                        .map(HttpCookie::getValue)
                        .findFirst()
                        .orElseThrow();
        byte[] body = registration("x".repeat(200_000)).getBytes(StandardCharsets.UTF_8);

        HttpResponse<String> response =
                client.send(
                        HttpRequest.newBuilder(
                                        URI.create(
                                                "http://localhost:" + port + "/api/auth/register"))
                                .header("Content-Type", "application/json")
                                .header("X-XSRF-TOKEN", token)
                                // An input stream publisher sends the body chunked: no
                                // Content-Length
                                .POST(
                                        HttpRequest.BodyPublishers.ofInputStream(
                                                () -> new ByteArrayInputStream(body)))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(HttpTestClient.contentType(response)).contains("application/problem+json");
    }

    @Test
    void anOversizedLoginFormIsRefusedToo() {
        HttpTestClient http = new HttpTestClient(port);

        HttpResponse<String> response =
                http.postForm(
                        "/api/auth/login",
                        Map.of("email", "a@example.com", "password", "x".repeat(200_000)),
                        http.csrfToken());

        assertThat(response.statusCode()).isEqualTo(413);
    }

    @Test
    void ordinaryRequestsAreUnaffected() {
        HttpTestClient http = new HttpTestClient(port);

        HttpResponse<String> response =
                http.postJson("/api/auth/register", registration("Ada"), http.csrfToken());

        assertThat(response.statusCode()).isEqualTo(202);
    }
}
