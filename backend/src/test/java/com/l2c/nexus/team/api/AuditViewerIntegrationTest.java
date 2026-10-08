package com.l2c.nexus.team.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.l2c.nexus.TestcontainersConfiguration;
import com.l2c.nexus.support.CrossTenantAssertions;
import com.l2c.nexus.support.TenantWorld;
import com.l2c.nexus.support.TenantWorld.Member;
import com.l2c.nexus.support.TenantWorldFactory;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class AuditViewerIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired private TenantWorldFactory factory;
    @Autowired private JdbcClient jdbc;

    private TenantWorld world;

    @BeforeEach
    void setUp() {
        world = factory.create(port);
    }

    private HttpResponse<String> audit(Member as, UUID org, String query) {
        return as.http().get("/api/orgs/" + org + "/audit" + query);
    }

    private HttpResponse<String> audit(Member as, String query) {
        return audit(as, world.orgA(), query);
    }

    private void createDepartment(Member as, UUID org, String name) {
        HttpResponse<String> response =
                as.http()
                        .postJson(
                                "/api/orgs/" + org + "/departments",
                                "{\"name\":\"" + name + "\"}",
                                as.http().csrfToken());
        assertThat(response.statusCode()).isEqualTo(201);
    }

    private UUID membershipOf(Member member) {
        return jdbc.sql("SELECT id FROM memberships WHERE organization_id = ? AND user_id = ?")
                .params(world.orgA(), member.userId())
                .query(UUID.class)
                .single();
    }

    private static List<String> eventTypes(String body) {
        Matcher matcher = Pattern.compile("\"eventType\":\"([A-Z_]+)\"").matcher(body);
        List<String> types = new ArrayList<>();
        while (matcher.find()) {
            types.add(matcher.group(1));
        }
        return types;
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    @Test
    void theAuditLogIsInvisibleToEveryoneOutsideTheOrganization() {
        CrossTenantAssertions.assertIsolated(
                world, (client, segment) -> client.get("/api/orgs/" + segment + "/audit"));
    }

    @Test
    void onlyOwnersAndAdminsCanReadTheLog() {
        assertThat(audit(world.alice(), "").statusCode()).isEqualTo(200);
        assertThat(audit(world.ann(), "").statusCode()).isEqualTo(200);
        assertThat(audit(world.dan(), "").statusCode()).isEqualTo(403);
        assertThat(audit(world.carol(), "").statusCode()).isEqualTo(403);
    }

    @Test
    void eventsAreListedNewestFirstWithTheActorAndAllowListedMetadata() {
        createDepartment(world.alice(), world.orgA(), "Ops");
        HttpResponse<String> change =
                world.alice()
                        .http()
                        .request(
                                "PATCH",
                                "/api/orgs/"
                                        + world.orgA()
                                        + "/members/"
                                        + membershipOf(world.carol()),
                                "{\"role\":\"MANAGER\",\"version\":0}",
                                world.alice().http().csrfToken());
        assertThat(change.statusCode()).isEqualTo(200);

        HttpResponse<String> response = audit(world.alice(), "?size=100");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(eventTypes(response.body()))
                .containsExactly(
                        "MEMBER_ROLE_CHANGED", "DEPARTMENT_CREATED", "ORGANIZATION_CREATED");
        assertThat(response.body())
                .contains("\"totalElements\":3")
                .contains("\"actorName\":\"alice\"")
                .contains("\"fromRole\":\"EMPLOYEE\"")
                .contains("\"toRole\":\"MANAGER\"")
                .contains("\"name\":\"Ops\"")
                .contains("\"targetType\":\"MEMBERSHIP\"");
    }

    @Test
    void theLogCanBeFilteredByEventType() {
        createDepartment(world.alice(), world.orgA(), "Ops");

        HttpResponse<String> response = audit(world.alice(), "?eventType=DEPARTMENT_CREATED");

        assertThat(eventTypes(response.body())).containsExactly("DEPARTMENT_CREATED");
        assertThat(response.body()).contains("\"totalElements\":1");
    }

    @Test
    void theLogCanBeFilteredByInclusiveDays() {
        createDepartment(world.alice(), world.orgA(), "Ops");

        assertThat(audit(world.alice(), "?from=" + today()).body()).contains("\"totalElements\":2");
        assertThat(audit(world.alice(), "?to=" + today()).body()).contains("\"totalElements\":2");
        assertThat(audit(world.alice(), "?from=" + today().plusDays(1)).body())
                .contains("\"totalElements\":0");
        assertThat(audit(world.alice(), "?to=" + today().minusDays(1)).body())
                .contains("\"totalElements\":0");
    }

    @Test
    void theLogIsPaginated() {
        createDepartment(world.alice(), world.orgA(), "One");
        createDepartment(world.alice(), world.orgA(), "Two");
        createDepartment(world.alice(), world.orgA(), "Three");

        HttpResponse<String> first = audit(world.alice(), "?page=0&size=3");
        HttpResponse<String> second = audit(world.alice(), "?page=1&size=3");

        assertThat(first.body()).contains("\"totalElements\":4").contains("\"totalPages\":2");
        assertThat(eventTypes(first.body())).hasSize(3);
        assertThat(eventTypes(second.body())).containsExactly("ORGANIZATION_CREATED");
        assertThat(audit(world.alice(), "?size=100000").body()).contains("\"size\":100");
    }

    @Test
    void invalidParametersAreRejected() {
        assertThat(audit(world.alice(), "?eventType=NOPE").statusCode()).isEqualTo(400);
        assertThat(audit(world.alice(), "?from=yesterday").statusCode()).isEqualTo(400);
        assertThat(audit(world.alice(), "?from=2026-13-45").statusCode()).isEqualTo(400);
        assertThat(audit(world.alice(), "?to=%2B999999999-01-01").statusCode()).isEqualTo(400);
    }

    @Test
    void onlyThisOrganizationsEventsAppearAndNeverPlatformEvents() {
        createDepartment(world.bob(), world.orgB(), "Bobs Department");
        UUID platformActor = UUID.randomUUID();
        jdbc.sql(
                        "INSERT INTO audit_logs (id, organization_id, actor_user_id, event_type, metadata,"
                                + " occurred_at) VALUES (?, NULL, ?, 'USER_EMAIL_VERIFIED', CAST('{}' AS jsonb), now())")
                .params(UUID.randomUUID(), platformActor)
                .update();

        HttpResponse<String> response = audit(world.alice(), "?size=100");

        assertThat(response.body())
                .doesNotContain("Bobs Department")
                .doesNotContain(world.bob().userId().toString())
                .doesNotContain(platformActor.toString());
        assertThat(eventTypes(response.body())).containsExactly("ORGANIZATION_CREATED");
    }

    @Test
    void theLogNeverContainsEmailAddressesOrReviewComments() {
        var alice = world.alice().http();
        alice.postJson(
                "/api/orgs/" + world.orgA() + "/invitations",
                "{\"email\":\"" + world.erin().email() + "\",\"role\":\"EMPLOYEE\"}",
                alice.csrfToken());
        var carol = world.carol().http();
        String requests = "/api/orgs/" + world.orgA() + "/requests";
        HttpResponse<String> created =
                carol.postJson(
                        requests, "{\"title\":\"Desk\",\"category\":\"HR\"}", carol.csrfToken());
        String id =
                Pattern.compile("\"id\":\"([0-9a-f-]{36})\"")
                        .matcher(created.body())
                        .results()
                        .findFirst()
                        .orElseThrow()
                        .group(1);
        carol.postJson(
                requests + "/" + id + "/transitions",
                "{\"action\":\"SUBMIT\",\"version\":0}",
                carol.csrfToken());
        var dan = world.dan().http();
        dan.postJson(
                requests + "/" + id + "/transitions",
                "{\"action\":\"APPROVE\",\"version\":1,\"comment\":\"Top secret rationale\"}",
                dan.csrfToken());

        HttpResponse<String> response = audit(world.alice(), "?size=100");

        assertThat(response.body())
                .contains("INVITATION_CREATED")
                .contains("REQUEST_APPROVED")
                .contains("REQ-000001");
        assertThat(response.body())
                .doesNotContain("@")
                .doesNotContain("Top secret")
                .doesNotContain(world.erin().email());
    }

    @Test
    void anAdministratorWhoWasRemovedCanNoLongerReadTheLog() {
        assertThat(audit(world.ann(), "").statusCode()).isEqualTo(200);
        jdbc.sql(
                        "UPDATE memberships SET status = 'REVOKED' WHERE organization_id = ? AND user_id = ?")
                .params(world.orgA(), world.ann().userId())
                .update();

        assertThat(audit(world.ann(), "").statusCode()).isEqualTo(404);
    }
}
