package com.l2c.nexus.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.l2c.nexus.TestcontainersConfiguration;
import com.l2c.nexus.support.PlatformFixtures;
import com.l2c.nexus.support.PlatformFixtures.Account;
import com.l2c.nexus.support.TenantWorld;
import com.l2c.nexus.support.TenantWorldFactory;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class PlatformGateIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired private PlatformFixtures fixtures;
    @Autowired private TenantWorldFactory factory;
    @Autowired private JdbcClient jdbc;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mappings;

    private TenantWorld world;

    @BeforeEach
    void setUp() {
        world = factory.create(port);
    }

    @Test
    void anonymousCallersAreUnauthorized() {
        var anonymous = world.anonymous();

        assertThat(anonymous.get("/api/platform/organizations").statusCode()).isEqualTo(401);
        assertThat(
                        anonymous
                                .postJson(
                                        "/api/platform/organizations/" + world.orgA() + "/suspend",
                                        "{\"reason\":\"OTHER\",\"version\":0}",
                                        anonymous.csrfToken())
                                .statusCode())
                .isEqualTo(401);
        assertThat(anonymous.get("/api/platform/audit").statusCode()).isEqualTo(401);
    }

    @Test
    void ordinaryUsersCannotTellThePlatformApiExists() {
        Account user = fixtures.signedIn(port, false);

        HttpResponse<String> platform = user.http().get("/api/platform/organizations");
        HttpResponse<String> unknownTenantRoute =
                user.http().get("/api/orgs/" + UUID.randomUUID() + "/x");

        assertThat(platform.statusCode()).isEqualTo(404);
        assertThat(platform.body())
                .as("the same body as any other hidden route")
                .isEqualTo(unknownTenantRoute.body());
        assertThat(user.http().get("/api/platform/audit").statusCode()).isEqualTo(404);
        assertThat(user.http().get("/api/platform/anything/at/all").statusCode()).isEqualTo(404);
    }

    @Test
    void organizationOwnersAndAdministratorsGetNoPlatformAccess() {
        for (var member : List.of(world.alice(), world.ann(), world.dan(), world.bob())) {
            assertThat(member.http().get("/api/platform/organizations").statusCode())
                    .isEqualTo(404);
        }
    }

    @Test
    void aPlatformAdministratorCanUseThePlatformApi() {
        Account admin = fixtures.signedIn(port, true);

        assertThat(admin.http().get("/api/platform/organizations").statusCode()).isEqualTo(200);
        assertThat(admin.http().get("/api/platform/audit").statusCode()).isEqualTo(200);
    }

    @Test
    void aDemotedAdministratorLosesAccessOnTheNextRequest() {
        Account admin = fixtures.signedIn(port, true);
        assertThat(admin.http().get("/api/platform/organizations").statusCode()).isEqualTo(200);

        fixtures.setPlatformAdmin(admin.userId(), false);

        assertThat(admin.http().get("/api/platform/organizations").statusCode()).isEqualTo(404);
    }

    @Test
    void aDisabledAdministratorLosesTheSession() {
        Account admin = fixtures.signedIn(port, true);
        jdbc.sql("UPDATE users SET status = 'DISABLED', updated_at = ? WHERE id = ?")
                .params(
                        java.time.OffsetDateTime.ofInstant(Instant.now(), java.time.ZoneOffset.UTC),
                        admin.userId())
                .update();

        assertThat(admin.http().get("/api/platform/organizations").statusCode()).isEqualTo(401);
    }

    @Test
    void everyPlatformRouteIsHiddenFromOrdinaryUsers() {
        Account user = fixtures.signedIn(port, false);
        List<String> checked = new ArrayList<>();

        for (RequestMappingInfo info : mappings.getHandlerMethods().keySet()) {
            if (info.getPathPatternsCondition() == null) {
                continue;
            }
            for (String pattern : info.getPathPatternsCondition().getPatternValues()) {
                if (!pattern.startsWith("/api/platform")) {
                    continue;
                }
                String path = pattern.replaceAll("\\{[^/}]+}", UUID.randomUUID().toString());
                Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
                for (RequestMethod method :
                        methods.isEmpty() ? Set.of(RequestMethod.GET) : methods) {
                    boolean hasBody = method != RequestMethod.GET && method != RequestMethod.DELETE;
                    HttpResponse<String> response =
                            user.http()
                                    .request(
                                            method.name(),
                                            path,
                                            hasBody ? "{}" : null,
                                            user.http().csrfToken());
                    assertThat(response.statusCode())
                            .as(method + " " + pattern + " must be hidden from ordinary users")
                            .isEqualTo(404);
                    checked.add(method + " " + pattern);
                }
            }
        }

        assertThat(checked)
                .as("platform routes found by the inventory")
                .hasSizeGreaterThanOrEqualTo(4);
    }

    @Test
    void platformAdministratorsHaveNoTenantPrivilegesBecauseOfIt() {
        Account outsider = fixtures.signedIn(port, true);
        // carol is an ordinary Employee of Org A who also holds the platform role
        fixtures.setPlatformAdmin(world.carol().userId(), true);

        // Not a member: the tenant gate treats them like anyone else
        assertThat(outsider.http().get("/api/orgs/" + world.orgA()).statusCode()).isEqualTo(404);
        assertThat(outsider.http().get("/api/orgs/" + world.orgA() + "/dashboard").statusCode())
                .isEqualTo(404);
        assertThat(outsider.http().get("/api/orgs/" + world.orgA() + "/audit").statusCode())
                .isEqualTo(404);
        assertThat(outsider.http().get("/api/orgs").body()).isEqualTo("[]");
        // A member who is also a platform administrator has exactly the member's rights
        assertThat(world.carol().http().get("/api/orgs/" + world.orgA() + "/audit").statusCode())
                .isEqualTo(403);
        assertThat(world.carol().http().get("/api/orgs/" + world.orgB()).statusCode())
                .isEqualTo(404);
        assertThat(world.carol().http().get("/api/platform/organizations").statusCode())
                .isEqualTo(200);
    }
}
