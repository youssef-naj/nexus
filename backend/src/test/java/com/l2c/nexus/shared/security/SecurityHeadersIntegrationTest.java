package com.l2c.nexus.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.l2c.nexus.TestcontainersConfiguration;
import com.l2c.nexus.support.HttpTestClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class SecurityHeadersIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    private HttpTestClient http;

    @BeforeEach
    void setUp() {
        http = new HttpTestClient(port);
    }

    private static void assertHardened(HttpResponse<?> response, String situation) {
        HttpHeaders headers = response.headers();
        assertThat(headers.firstValue("Content-Security-Policy"))
                .as(situation)
                .contains(
                        "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'");
        assertThat(headers.firstValue("Referrer-Policy")).as(situation).contains("no-referrer");
        assertThat(headers.firstValue("X-Content-Type-Options")).as(situation).contains("nosniff");
        assertThat(headers.firstValue("X-Frame-Options")).as(situation).contains("DENY");
        assertThat(headers.firstValue("Cache-Control"))
                .as(situation)
                .hasValueSatisfying(value -> assertThat(value).contains("no-store"));
        assertThat(headers.firstValue("Permissions-Policy"))
                .as(situation)
                .hasValueSatisfying(value -> assertThat(value).contains("camera=()"));
        assertThat(headers.firstValue("Cross-Origin-Opener-Policy"))
                .as(situation)
                .contains("same-origin");
        assertThat(headers.firstValue("Cross-Origin-Resource-Policy"))
                .as(situation)
                .contains("same-origin");
    }

    @Test
    void aSuccessfulResponseCarriesTheFullSetOfSecurityHeaders() {
        HttpResponse<String> response = http.get("/api/system/ping");

        assertThat(response.statusCode()).isEqualTo(200);
        assertHardened(response, "200 ping");
    }

    @Test
    void errorResponsesFromEveryLayerCarryTheSameHeaders() {
        String registerPath = "/api/auth/register";
        String csrf = http.csrfToken();
        String oversize = "{\"displayName\":\"" + "x".repeat(200_000) + "\"}";

        HttpResponse<String> unauthorized = http.get("/api/orgs");
        HttpResponse<String> csrfRefused = http.postJson(registerPath, "{}", null);
        HttpResponse<String> invalid = http.postJson(registerPath, "{}", csrf);
        HttpResponse<String> tooLarge = http.postJson(registerPath, oversize, csrf);

        assertThat(unauthorized.statusCode()).isEqualTo(401);
        assertThat(csrfRefused.statusCode()).isEqualTo(403);
        assertThat(invalid.statusCode()).isEqualTo(400);
        assertThat(tooLarge.statusCode()).isEqualTo(413);
        for (Map.Entry<String, HttpResponse<String>> entry :
                Map.of("401", unauthorized, "403", csrfRefused, "400", invalid, "413", tooLarge)
                        .entrySet()) {
            assertHardened(entry.getValue(), entry.getKey());
        }
    }

    @Test
    void nothingRevealsTheServerAndHstsIsNotSentOverPlainHttp() {
        HttpResponse<String> response = http.get("/api/system/ping");

        assertThat(response.headers().firstValue("Server")).isEmpty();
        assertThat(response.headers().firstValue("X-Powered-By")).isEmpty();
        // HSTS only makes sense (and is only sent) over HTTPS
        assertThat(response.headers().firstValue("Strict-Transport-Security")).isEmpty();
    }
}
