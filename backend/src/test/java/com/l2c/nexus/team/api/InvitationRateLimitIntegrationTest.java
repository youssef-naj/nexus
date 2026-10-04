package com.l2c.nexus.team.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.l2c.nexus.TestcontainersConfiguration;
import com.l2c.nexus.support.HttpTestClient;
import com.l2c.nexus.support.TenantWorld;
import com.l2c.nexus.support.TenantWorldFactory;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "nexus.rate-limit.invite-per-org.limit=2")
@Import(TestcontainersConfiguration.class)
class InvitationRateLimitIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired private TenantWorldFactory factory;

    @Test
    void anOrganizationCannotFloodInvitations() {
        TenantWorld world = factory.create(port);
        var alice = world.alice().http();

        int[] statuses = new int[3];
        HttpResponse<String> last = null;
        for (int i = 0; i < 3; i++) {
            last =
                    alice.postJson(
                            "/api/orgs/" + world.orgA() + "/invitations",
                            "{\"email\":\"flood-"
                                    + UUID.randomUUID()
                                    + "@example.com\",\"role\":\"EMPLOYEE\"}",
                            alice.csrfToken());
            statuses[i] = last.statusCode();
        }

        assertThat(statuses).containsExactly(201, 201, 429);
        assertThat(HttpTestClient.retryAfterSeconds(last)).isBetween(1L, 60L);
    }
}
