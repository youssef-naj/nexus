package com.l2c.nexus.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.l2c.nexus.TestcontainersConfiguration;
import com.l2c.nexus.support.VerifiedUserFactory;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "nexus.security.secure-cookies=true",
            "server.servlet.session.cookie.secure=true"
        })
@Import(TestcontainersConfiguration.class)
class SecureCookiesIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired private VerifiedUserFactory users;

    private static String setCookie(HttpResponse<?> response, String name) {
        List<String> all = response.headers().allValues("Set-Cookie");
        return all.stream()
                .filter(value -> value.startsWith(name + "="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No Set-Cookie for " + name + " in " + all));
    }

    @Test
    void bothCookiesAreMarkedSecureWhenTheDeployedSettingsAreOn() throws Exception {
        String email = users.create();
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> ping =
                client.send(
                        HttpRequest.newBuilder(
                                        URI.create("http://localhost:" + port + "/api/system/ping"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        String csrfCookie = setCookie(ping, "XSRF-TOKEN");
        String token = csrfCookie.substring("XSRF-TOKEN=".length(), csrfCookie.indexOf(';'));

        // A browser would not send a Secure cookie over plain HTTP, so the test sends it by hand
        String form =
                "email="
                        + URLEncoder.encode(email, StandardCharsets.UTF_8)
                        + "&password="
                        + URLEncoder.encode(VerifiedUserFactory.PASSWORD, StandardCharsets.UTF_8);
        HttpResponse<String> login =
                client.send(
                        HttpRequest.newBuilder(
                                        URI.create("http://localhost:" + port + "/api/auth/login"))
                                .header("Content-Type", "application/x-www-form-urlencoded")
                                .header("X-XSRF-TOKEN", token)
                                .header("Cookie", "XSRF-TOKEN=" + token)
                                .POST(HttpRequest.BodyPublishers.ofString(form))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());

        assertThat(csrfCookie).containsIgnoringCase("Secure").containsIgnoringCase("SameSite=Lax");
        assertThat(login.statusCode()).isEqualTo(204);
        assertThat(setCookie(login, "SESSION"))
                .containsIgnoringCase("Secure")
                .containsIgnoringCase("HttpOnly")
                .containsIgnoringCase("SameSite=Lax");
    }
}
