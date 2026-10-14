package com.l2c.nexus.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.l2c.nexus.TestcontainersConfiguration;
import com.l2c.nexus.support.Parallel;
import com.l2c.nexus.support.PlatformFixtures;
import com.l2c.nexus.support.PlatformFixtures.Account;
import com.l2c.nexus.support.TenantWorld;
import com.l2c.nexus.support.TenantWorldFactory;
import com.l2c.nexus.team.application.RecordingInvitationEmails;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
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
class PlatformOrganizationsIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired private PlatformFixtures fixtures;
    @Autowired private TenantWorldFactory factory;
    @Autowired private RecordingInvitationEmails invitationEmails;
    @Autowired private JdbcClient jdbc;

    private TenantWorld world;
    private Account admin;

    @BeforeEach
    void setUp() {
        world = factory.create(port);
        admin = fixtures.signedIn(port, true);
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String nameOf(UUID organization) {
        return jdbc.sql("SELECT name FROM organizations WHERE id = ?")
                .param(organization)
                .query(String.class)
                .single();
    }

    private HttpResponse<String> search(String query) {
        return admin.http().get("/api/platform/organizations" + query);
    }

    /** The JSON object of one organization from the list, found by searching for its name. */
    private String row(UUID organization) {
        String body = search("?q=" + enc(nameOf(organization))).body();
        Matcher matcher =
                Pattern.compile("\\{[^{}]*\"id\":\"" + organization + "\"[^{}]*}").matcher(body);
        assertThat(matcher.find()).as("organization in " + body).isTrue();
        return matcher.group();
    }

    private long versionOf(String json) {
        Matcher matcher = Pattern.compile("\"version\":(\\d+)").matcher(json);
        assertThat(matcher.find()).as("a version in " + json).isTrue();
        return Long.parseLong(matcher.group(1));
    }

    private HttpResponse<String> act(
            Account as, UUID organization, String action, String reason, long version) {
        return as.http()
                .postJson(
                        "/api/platform/organizations/" + organization + "/" + action,
                        "{\"reason\":\"" + reason + "\",\"version\":" + version + "}",
                        as.http().csrfToken());
    }

    private HttpResponse<String> suspend(UUID organization) {
        return act(
                admin, organization, "suspend", "POLICY_VIOLATION", versionOf(row(organization)));
    }

    private HttpResponse<String> reactivate(UUID organization) {
        return act(
                admin, organization, "reactivate", "ISSUE_RESOLVED", versionOf(row(organization)));
    }

    private int count(String sql, Object... params) {
        return jdbc.sql(sql).params(params).query(Integer.class).single();
    }

    private UUID insertOrganization(String name, String slug) {
        UUID id = UUID.randomUUID();
        jdbc.sql(
                        "INSERT INTO organizations (id, name, slug, created_at, updated_at) VALUES (?, ?, ?, now(), now())")
                .params(id, name, slug)
                .update();
        return id;
    }

    private static List<String> names(String body) {
        Matcher matcher = Pattern.compile("\"name\":\"([^\"]*)\"").matcher(body);
        List<String> found = new ArrayList<>();
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }

    @Test
    void theListHoldsMetadataOnlyAndNothingAboutThePeopleInside() {
        String json = row(world.orgA());

        Matcher keys = Pattern.compile("\"([A-Za-z]+)\":").matcher(json);
        TreeSet<String> found = new TreeSet<>();
        while (keys.find()) {
            found.add(keys.group(1));
        }
        assertThat(found)
                .containsExactly(
                        "createdAt", "id", "memberCount", "name", "slug", "status", "version");
        String everything = search("?size=100").body();
        for (var member : List.of(world.alice(), world.ann(), world.dan(), world.carol())) {
            assertThat(everything)
                    .doesNotContain(member.email())
                    .doesNotContain(member.userId().toString());
        }
        assertThat(json).contains("\"status\":\"ACTIVE\"").contains("\"memberCount\":5");
    }

    @Test
    void memberCountsOnlyCountActiveMembers() {
        jdbc.sql(
                        "UPDATE memberships SET status = 'REVOKED' WHERE organization_id = ? AND user_id = ?")
                .params(world.orgA(), world.frank().userId())
                .update();

        assertThat(row(world.orgA())).contains("\"memberCount\":4");
        assertThat(row(world.orgB())).contains("\"memberCount\":1");
    }

    @Test
    void searchFiltersPaginatesAndSorts() {
        String token = "x" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        insertOrganization(token + " Bravo", token + "-bravo");
        insertOrganization(token + " Alpha", token + "-alpha");
        UUID charlie = insertOrganization(token + " Charlie", token + "-charlie");
        jdbc.sql("UPDATE organizations SET status = 'SUSPENDED' WHERE id = ?")
                .param(charlie)
                .update();

        HttpResponse<String> all = search("?q=" + token.toUpperCase());
        assertThat(names(all.body()))
                .containsExactly(token + " Alpha", token + " Bravo", token + " Charlie");
        assertThat(all.body()).contains("\"totalElements\":3");
        assertThat(names(search("?q=" + token + "&sort=NAME&direction=DESC").body()))
                .containsExactly(token + " Charlie", token + " Bravo", token + " Alpha");
        HttpResponse<String> secondPage = search("?q=" + token + "&size=2&page=1");
        assertThat(names(secondPage.body())).containsExactly(token + " Charlie");
        assertThat(secondPage.body()).contains("\"totalPages\":2");
        assertThat(names(search("?q=" + token + "&status=SUSPENDED").body()))
                .containsExactly(token + " Charlie");
        assertThat(names(search("?q=" + token + "&status=ACTIVE").body())).hasSize(2);
        assertThat(search("?q=" + token + "&sort=CREATED").statusCode()).isEqualTo(200);
        assertThat(search("?q=" + token + "-bravo").body()).contains(token + " Bravo");
        assertThat(search("?sort=password").statusCode()).isEqualTo(400);
        assertThat(search("?status=NOPE").statusCode()).isEqualTo(400);
        assertThat(search("?size=100000").body()).contains("\"size\":100");
    }

    @Test
    void searchTextIsNeverTreatedAsWildcards() {
        String token = "x" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        insertOrganization(token + " 100% club", token + "-percent");
        insertOrganization(token + " plain", token + "-plain");

        assertThat(names(search("?q=" + enc(token + " 100%")).body()))
                .containsExactly(token + " 100% club");
        assertThat(names(search("?q=" + enc(token + " 1%0")).body())).isEmpty();
        assertThat(search("?q=" + enc("' OR 1=1 --")).body()).contains("\"totalElements\":0");
    }

    @Test
    void suspendingBlocksMembersImmediatelyAndOnlyThem() {
        HttpResponse<String> response = suspend(world.orgA());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("\"status\":\"SUSPENDED\"")
                .contains("\"memberCount\":5");
        for (String path : List.of("", "/dashboard", "/requests", "/members", "/departments")) {
            HttpResponse<String> member =
                    world.carol().http().get("/api/orgs/" + world.orgA() + path);
            assertThat(member.statusCode()).as(path).isEqualTo(403);
            assertThat(member.body()).contains("ORGANIZATION_SUSPENDED");
        }
        assertThat(world.alice().http().get("/api/orgs/" + world.orgA() + "/audit").statusCode())
                .isEqualTo(403);
        assertThat(world.bob().http().get("/api/orgs/" + world.orgB()).statusCode()).isEqualTo(200);
        assertThat(world.erin().http().get("/api/orgs/" + world.orgA()).statusCode())
                .as("outsiders learn nothing")
                .isEqualTo(404);
        assertThat(admin.http().get("/api/orgs/" + world.orgA()).statusCode())
                .as("the platform is not a member")
                .isEqualTo(404);
    }

    @Test
    void reactivatingRestoresAccess() {
        suspend(world.orgA());
        assertThat(world.carol().http().get("/api/orgs/" + world.orgA()).statusCode())
                .isEqualTo(403);

        HttpResponse<String> response = reactivate(world.orgA());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"ACTIVE\"");
        assertThat(world.carol().http().get("/api/orgs/" + world.orgA()).statusCode())
                .isEqualTo(200);
        assertThat(
                        world.carol()
                                .http()
                                .get("/api/orgs/" + world.orgA() + "/dashboard")
                                .statusCode())
                .isEqualTo(200);
    }

    @Test
    void suspendedOrganizationsStayInTheirMembersListWithTheirStatus() {
        suspend(world.orgA());

        HttpResponse<String> list = world.alice().http().get("/api/orgs");

        assertThat(list.statusCode()).isEqualTo(200);
        assertThat(list.body()).contains("\"status\":\"SUSPENDED\"").contains(nameOf(world.orgA()));
    }

    @Test
    void pendingInvitationsCannotBeAcceptedWhileSuspended() {
        var alice = world.alice().http();
        alice.postJson(
                "/api/orgs/" + world.orgA() + "/invitations",
                "{\"email\":\"" + world.erin().email() + "\",\"role\":\"EMPLOYEE\"}",
                alice.csrfToken());
        String token = invitationEmails.awaitNext(world.erin().email()).token();
        suspend(world.orgA());

        var erin = world.erin().http();
        HttpResponse<String> accepted =
                erin.postJson(
                        "/api/invitations/accept",
                        "{\"token\":\"" + token + "\"}",
                        erin.csrfToken());

        assertThat(accepted.statusCode()).isEqualTo(400);
        assertThat(world.erin().http().get("/api/orgs/" + world.orgA()).statusCode())
                .isEqualTo(404);
    }

    @Test
    void suspendingTwiceChangesAndAuditsOnlyOnce() {
        HttpResponse<String> first = suspend(world.orgA());
        long afterFirst = versionOf(first.body());

        HttpResponse<String> second =
                act(admin, world.orgA(), "suspend", "NON_PAYMENT", afterFirst);

        assertThat(second.statusCode()).isEqualTo(200);
        assertThat(versionOf(second.body())).isEqualTo(afterFirst);
        assertThat(
                        count(
                                "SELECT count(*) FROM audit_logs WHERE event_type = 'ORGANIZATION_SUSPENDED' AND organization_id = ?",
                                world.orgA()))
                .isEqualTo(1);
        assertThat(
                        count(
                                "SELECT count(*) FROM audit_logs WHERE event_type = 'PLATFORM_ORGANIZATION_SUSPENDED' AND target_id = ?",
                                world.orgA()))
                .isEqualTo(1);
    }

    @Test
    void aStaleVersionIsRejected() {
        long seen = versionOf(row(world.orgA()));
        suspend(world.orgA());

        HttpResponse<String> response =
                act(admin, world.orgA(), "reactivate", "ISSUE_RESOLVED", seen);

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.body()).contains("STALE_VERSION");
        assertThat(world.carol().http().get("/api/orgs/" + world.orgA()).statusCode())
                .isEqualTo(403);
    }

    @Test
    void reasonsMustSuitTheAction() {
        long version = versionOf(row(world.orgA()));

        HttpResponse<String> wrongForSuspend =
                act(admin, world.orgA(), "suspend", "ISSUE_RESOLVED", version);
        HttpResponse<String> wrongForReactivate =
                act(admin, world.orgA(), "reactivate", "NON_PAYMENT", version);

        assertThat(wrongForSuspend.statusCode()).isEqualTo(400);
        assertThat(wrongForSuspend.body()).contains("\"errors\"").contains("\"reason\"");
        assertThat(wrongForReactivate.statusCode()).isEqualTo(400);
        assertThat(row(world.orgA())).contains("\"status\":\"ACTIVE\"");
    }

    @Test
    void missingOrUnknownInputIsRejected() {
        var http = admin.http();
        String path = "/api/platform/organizations/" + world.orgA() + "/suspend";

        assertThat(http.postJson(path, "{\"version\":0}", http.csrfToken()).statusCode())
                .isEqualTo(400);
        assertThat(http.postJson(path, "{\"reason\":\"OTHER\"}", http.csrfToken()).statusCode())
                .isEqualTo(400);
        assertThat(
                        http.postJson(
                                        path,
                                        "{\"reason\":\"BECAUSE_I_SAID_SO\",\"version\":0}",
                                        http.csrfToken())
                                .statusCode())
                .isEqualTo(400);
        assertThat(
                        http.postJson(
                                        path,
                                        "{\"reason\":\"OTHER\",\"version\":-1}",
                                        http.csrfToken())
                                .statusCode())
                .isEqualTo(400);
        assertThat(row(world.orgA())).contains("\"status\":\"ACTIVE\"");
    }

    @Test
    void unknownOrganizationsAreNotFound() {
        assertThat(act(admin, UUID.randomUUID(), "suspend", "OTHER", 0).statusCode())
                .isEqualTo(404);
        assertThat(act(admin, UUID.randomUUID(), "reactivate", "OTHER", 0).statusCode())
                .isEqualTo(404);
    }

    @Test
    void platformActionsRequireACsrfToken() {
        HttpResponse<String> response =
                admin.http()
                        .postJson(
                                "/api/platform/organizations/" + world.orgA() + "/suspend",
                                "{\"reason\":\"OTHER\",\"version\":0}",
                                null);

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(row(world.orgA())).contains("\"status\":\"ACTIVE\"");
    }

    @Test
    void bothSidesGetAnAuditRecordButTheTenantNeverSeesTheAdministrator() {
        suspend(world.orgA());
        reactivate(world.orgA());

        HttpResponse<String> tenant =
                world.alice()
                        .http()
                        .get(
                                "/api/orgs/"
                                        + world.orgA()
                                        + "/audit?eventType=ORGANIZATION_SUSPENDED");
        HttpResponse<String> platform =
                admin.http().get("/api/platform/audit?eventType=PLATFORM_ORGANIZATION_SUSPENDED");

        assertThat(tenant.statusCode()).isEqualTo(200);
        assertThat(tenant.body())
                .contains("\"eventType\":\"ORGANIZATION_SUSPENDED\"")
                .contains("\"actorName\":null")
                .contains("\"reason\":\"POLICY_VIOLATION\"")
                .doesNotContain(admin.userId().toString())
                .doesNotContain(admin.email());
        assertThat(platform.statusCode()).isEqualTo(200);
        assertThat(platform.body())
                .contains(nameOf(world.orgA()))
                .contains("\"actorName\":\"Verified\"")
                .contains("\"reason\":\"POLICY_VIOLATION\"");
        assertThat(
                        world.alice()
                                .http()
                                .get(
                                        "/api/orgs/"
                                                + world.orgA()
                                                + "/audit?eventType=ORGANIZATION_REACTIVATED")
                                .body())
                .contains("\"reason\":\"ISSUE_RESOLVED\"");
    }

    @Test
    void twoAdministratorsActingOnTheSameVersionProduceExactlyOneWinner() throws Exception {
        Account second = fixtures.signedIn(port, true);
        long version = versionOf(row(world.orgA()));
        String firstCsrf = admin.http().csrfToken();
        String secondCsrf = second.http().csrfToken();
        String body = "{\"reason\":\"SECURITY_CONCERN\",\"version\":" + version + "}";
        String path = "/api/platform/organizations/" + world.orgA() + "/suspend";

        List<Integer> statuses =
                Parallel.statuses(
                        List.of(
                                () -> admin.http().postJson(path, body, firstCsrf),
                                () -> second.http().postJson(path, body, secondCsrf)));

        assertThat(statuses).containsExactly(200, 409);
        assertThat(
                        count(
                                "SELECT count(*) FROM audit_logs WHERE event_type = 'ORGANIZATION_SUSPENDED' AND organization_id = ?",
                                world.orgA()))
                .isEqualTo(1);
    }
}
