package com.l2c.nexus.organization.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.l2c.nexus.TestcontainersConfiguration;
import com.l2c.nexus.support.CrossTenantAssertions;
import com.l2c.nexus.support.HttpTestClient;
import com.l2c.nexus.support.TenantWorld;
import com.l2c.nexus.support.TenantWorldFactory;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class OrganizationGateIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired private TenantWorldFactory factory;
    @Autowired private JdbcClient jdbc;

    private TenantWorld world;

    @BeforeEach
    void setUp() {
        world = factory.create(port);
    }

    private static HttpResponse<String> organization(HttpTestClient http, String segment) {
        return http.get("/api/orgs/" + segment);
    }

    @Test
    void membersSeeTheirOrganizationWithTheirRoleAndPermissions() {
        HttpResponse<String> owner = organization(world.alice().http(), world.orgA().toString());
        HttpResponse<String> employee = organization(world.carol().http(), world.orgA().toString());

        assertThat(owner.statusCode()).isEqualTo(200);
        assertThat(owner.body()).contains("\"role\":\"OWNER\"").contains("ORGANIZATION_UPDATE");
        assertThat(employee.statusCode()).isEqualTo(200);
        assertThat(employee.body())
                .contains("\"role\":\"EMPLOYEE\"")
                .contains("REQUEST_CREATE")
                .doesNotContain("ORGANIZATION_UPDATE")
                .doesNotContain("MEMBER_INVITE");
    }

    @Test
    void theGateHidesTheOrganizationFromEveryoneOutsideIt() {
        CrossTenantAssertions.assertIsolated(world, OrganizationGateIntegrationTest::organization);
    }

    @Test
    void isolationWorksInBothDirections() {
        assertThat(organization(world.bob().http(), world.orgA().toString()).statusCode())
                .isEqualTo(404);
        assertThat(organization(world.alice().http(), world.orgB().toString()).statusCode())
                .isEqualTo(404);
        assertThat(organization(world.bob().http(), world.orgB().toString()).statusCode())
                .isEqualTo(200);
    }

    @Test
    void revokedMembersLoseAccessOnTheirNextRequest() {
        HttpTestClient frank = world.frank().http();
        assertThat(organization(frank, world.orgA().toString()).statusCode()).isEqualTo(200);

        jdbc.sql(
                        "UPDATE memberships SET status = 'REVOKED'"
                                + " WHERE organization_id = ? AND user_id = ?")
                .params(world.orgA(), world.frank().userId())
                .update();

        assertThat(organization(frank, world.orgA().toString()).statusCode()).isEqualTo(404);
    }

    @Test
    void suspendedOrganizationsAreForbiddenToMembersButStayInvisibleToOutsiders() {
        jdbc.sql("UPDATE organizations SET status = 'SUSPENDED' WHERE id = ?")
                .param(world.orgA())
                .update();

        HttpResponse<String> member = organization(world.alice().http(), world.orgA().toString());
        HttpResponse<String> outsider = organization(world.bob().http(), world.orgA().toString());
        HttpResponse<String> missing =
                organization(world.bob().http(), UUID.randomUUID().toString());

        assertThat(member.statusCode()).isEqualTo(403);
        assertThat(member.body()).contains("ORGANIZATION_SUSPENDED");
        // Outsiders must not learn that the organization exists, suspended or not
        assertThat(outsider.statusCode()).isEqualTo(404);
        assertThat(outsider.body()).isEqualTo(missing.body());
    }

    @Test
    void anOrganizationWithNoMatchingRouteStillRequiresMembership() {
        // The gate protects the whole /api/orgs/{id}/** space, not only routes that exist today
        HttpResponse<String> outsider =
                world.bob().http().get("/api/orgs/" + world.orgA() + "/anything/at/all");
        HttpResponse<String> missing =
                world.bob().http().get("/api/orgs/" + UUID.randomUUID() + "/anything/at/all");

        assertThat(outsider.statusCode()).isEqualTo(404);
        assertThat(outsider.body()).isEqualTo(missing.body());
    }
}
