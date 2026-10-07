package com.l2c.nexus.request.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.l2c.nexus.TestcontainersConfiguration;
import com.l2c.nexus.support.HttpTestClient;
import com.l2c.nexus.support.TenantWorld;
import com.l2c.nexus.support.TenantWorldFactory;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "nexus.rate-limit.request-create-per-member.limit=2")
@Import(TestcontainersConfiguration.class)
class RequestRateLimitIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired private TenantWorldFactory factory;

    @Test
    void aMemberCannotFloodRequestsButOthersAreUnaffected() {
        TenantWorld world = factory.create(port);
        var carol = world.carol().http();
        var dan = world.dan().http();
        String path = "/api/orgs/" + world.orgA() + "/requests";
        String body = "{\"title\":\"Spam\",\"category\":\"OTHER\"}";

        HttpResponse<String> first = carol.postJson(path, body, carol.csrfToken());
        HttpResponse<String> second = carol.postJson(path, body, carol.csrfToken());
        HttpResponse<String> third = carol.postJson(path, body, carol.csrfToken());

        assertThat(first.statusCode()).isEqualTo(201);
        assertThat(second.statusCode()).isEqualTo(201);
        assertThat(third.statusCode()).isEqualTo(429);
        assertThat(HttpTestClient.retryAfterSeconds(third)).isPositive();
        assertThat(dan.postJson(path, body, dan.csrfToken()).statusCode()).isEqualTo(201);
    }
}
