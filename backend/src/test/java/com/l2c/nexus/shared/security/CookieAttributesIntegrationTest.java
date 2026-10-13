package com.l2c.nexus.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.l2c.nexus.TestcontainersConfiguration;
import com.l2c.nexus.support.HttpTestClient;
import com.l2c.nexus.support.VerifiedUserFactory;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class CookieAttributesIntegrationTest {

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
    void theCsrfCookieIsSameSiteLaxReadableByScriptAndNotSecureOverHttp() {
        HttpResponse<String> response = new HttpTestClient(port).get("/api/system/ping");

        String cookie = setCookie(response, "XSRF-TOKEN");

        assertThat(cookie).containsIgnoringCase("SameSite=Lax");
        assertThat(cookie).as("JavaScript has to read it").doesNotContainIgnoringCase("HttpOnly");
        assertThat(cookie).doesNotContainIgnoringCase("Secure");
    }

    @Test
    void theSessionCookieIsHttpOnlyAndSameSiteLax() {
        String email = users.create();
        HttpTestClient http = new HttpTestClient(port);

        HttpResponse<String> login =
                http.postForm(
                        "/api/auth/login",
                        Map.of("email", email, "password", VerifiedUserFactory.PASSWORD),
                        http.csrfToken());

        assertThat(login.statusCode()).isEqualTo(204);
        String cookie = setCookie(login, "SESSION");
        assertThat(cookie).containsIgnoringCase("HttpOnly");
        assertThat(cookie).containsIgnoringCase("SameSite=Lax");
    }
}
