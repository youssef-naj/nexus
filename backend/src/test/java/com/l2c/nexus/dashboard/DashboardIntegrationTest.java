package com.l2c.nexus.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.l2c.nexus.TestcontainersConfiguration;
import com.l2c.nexus.support.CrossTenantAssertions;
import com.l2c.nexus.support.TenantWorld;
import com.l2c.nexus.support.TenantWorld.Member;
import com.l2c.nexus.support.TenantWorldFactory;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
class DashboardIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired private TenantWorldFactory factory;
    @Autowired private JdbcClient jdbc;

    private TenantWorld world;

    private record Req(String id, long version) {}

    @BeforeEach
    void setUp() {
        world = factory.create(port);
    }

    private static String requests(UUID org) {
        return "/api/orgs/" + org + "/requests";
    }

    private Req draftIn(Member as, UUID org, String title) {
        HttpResponse<String> response =
                as.http()
                        .postJson(
                                requests(org),
                                "{\"title\":\"" + title + "\",\"category\":\"HR\"}",
                                as.http().csrfToken());
        assertThat(response.statusCode()).as("creating " + title).isEqualTo(201);
        return new Req(
                first(response.body(), "\"id\":\"([0-9a-f-]{36})\""),
                Long.parseLong(first(response.body(), "\"version\":(\\d+)")));
    }

    private Req draft(Member as, String title) {
        return draftIn(as, world.orgA(), title);
    }

    private Req transitionIn(Member as, UUID org, Req request, String action, String comment) {
        String body =
                "{\"action\":\""
                        + action
                        + "\",\"version\":"
                        + request.version()
                        + (comment == null ? "" : ",\"comment\":\"" + comment + "\"")
                        + "}";
        HttpResponse<String> response =
                as.http()
                        .postJson(
                                requests(org) + "/" + request.id() + "/transitions",
                                body,
                                as.http().csrfToken());
        assertThat(response.statusCode()).as(action).isEqualTo(200);
        return new Req(request.id(), Long.parseLong(first(response.body(), "\"version\":(\\d+)")));
    }

    private Req transition(Member as, Req request, String action, String comment) {
        return transitionIn(as, world.orgA(), request, action, comment);
    }

    private Req submitted(Member as, String title) {
        return transition(as, draft(as, title), "SUBMIT", null);
    }

    private HttpResponse<String> dashboardResponse(Member as) {
        return as.http().get("/api/orgs/" + world.orgA() + "/dashboard");
    }

    private String dashboard(Member as) {
        HttpResponse<String> response = dashboardResponse(as);
        assertThat(response.statusCode()).isEqualTo(200);
        return response.body();
    }

    private long listTotal(Member as, String query) {
        String url = requests(world.orgA()) + (query.isEmpty() ? "?size=1" : query + "&size=1");
        return Long.parseLong(first(as.http().get(url).body(), "\"totalElements\":(\\d+)"));
    }

    private static String first(String body, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(body);
        assertThat(matcher.find()).as(regex + " in " + body).isTrue();
        return matcher.group(1);
    }

    private static List<String> all(String body, String name) {
        Matcher matcher = Pattern.compile("\"" + name + "\":\"([^\"]*)\"").matcher(body);
        List<String> values = new ArrayList<>();
        while (matcher.find()) {
            values.add(matcher.group(1));
        }
        return values;
    }

    /** The first map is the overall breakdown, the second is "mine". */
    private static List<Map<String, Long>> byStatusMaps(String body) {
        Matcher outer = Pattern.compile("\"byStatus\":\\{([^}]*)}").matcher(body);
        List<Map<String, Long>> maps = new ArrayList<>();
        while (outer.find()) {
            Map<String, Long> counts = new LinkedHashMap<>();
            Matcher pair = Pattern.compile("\"([A-Z_]+)\":(\\d+)").matcher(outer.group(1));
            while (pair.find()) {
                counts.put(pair.group(1), Long.parseLong(pair.group(2)));
            }
            maps.add(counts);
        }
        return maps;
    }

    /** The first total is the overall one, the second is "mine". */
    private static List<Long> totals(String body) {
        Matcher matcher = Pattern.compile("\"total\":(\\d+)").matcher(body);
        List<Long> totals = new ArrayList<>();
        while (matcher.find()) {
            totals.add(Long.parseLong(matcher.group(1)));
        }
        return totals;
    }

    private static String awaiting(String body) {
        return first(body, "\"awaitingReview\":(null|\\d+)");
    }

    @Test
    void theDashboardIsInvisibleToEveryoneOutsideTheOrganization() {
        CrossTenantAssertions.assertIsolated(
                world, (client, segment) -> client.get("/api/orgs/" + segment + "/dashboard"));
    }

    @Test
    void anOrganizationWithoutRequestsShowsZeroForEveryStatus() {
        String body = dashboard(world.alice());

        assertThat(body).contains("\"scope\":\"ORGANIZATION\"").contains("\"recent\":[]");
        assertThat(totals(body)).containsExactly(0L, 0L);
        assertThat(byStatusMaps(body).get(0).keySet())
                .containsExactlyInAnyOrder(
                        "DRAFT", "SUBMITTED", "CHANGES_REQUESTED", "APPROVED", "REJECTED");
        assertThat(byStatusMaps(body).get(0).values()).containsOnly(0L);
        assertThat(awaiting(body)).isEqualTo("0");
    }

    @Test
    void employeesSeeOnlyTheirOwnRequestsAndCannotReview() {
        draft(world.carol(), "Carol one");
        draft(world.carol(), "Carol two");
        submitted(world.frank(), "Frank submitted");

        String carol = dashboard(world.carol());
        String frank = dashboard(world.frank());

        assertThat(carol).contains("\"scope\":\"MINE\"");
        assertThat(totals(carol)).containsExactly(2L, 2L);
        assertThat(byStatusMaps(carol).get(0))
                .containsEntry("DRAFT", 2L)
                .containsEntry("SUBMITTED", 0L);
        assertThat(awaiting(carol)).isEqualTo("null");
        assertThat(carol).contains("\"recent\":[]").doesNotContain("Frank");
        assertThat(totals(frank)).containsExactly(1L, 1L);
        assertThat(byStatusMaps(frank).get(0)).containsEntry("SUBMITTED", 1L);
        assertThat(all(frank, "title")).containsExactly("Frank submitted");
    }

    @Test
    void reviewersSeeTheWholeOrganizationAndTheirOwnRequestsSeparately() {
        draft(world.carol(), "Carol draft");
        draft(world.carol(), "Carol draft two");
        submitted(world.frank(), "Frank submitted");
        draft(world.dan(), "Dan draft");

        String alice = dashboard(world.alice());
        String dan = dashboard(world.dan());

        assertThat(alice).contains("\"scope\":\"ORGANIZATION\"");
        assertThat(totals(alice)).containsExactly(4L, 0L);
        assertThat(byStatusMaps(alice).get(0))
                .containsEntry("DRAFT", 3L)
                .containsEntry("SUBMITTED", 1L);
        assertThat(totals(dan)).containsExactly(4L, 1L);
        assertThat(byStatusMaps(dan).get(1)).containsEntry("DRAFT", 1L);
    }

    @Test
    void awaitingReviewCountsOthersSubmissionsOnlyAndShrinksAsTheyAreDecided() {
        Req carols = submitted(world.carol(), "Carol submission");
        submitted(world.dan(), "Dan submission");

        assertThat(awaiting(dashboard(world.alice()))).isEqualTo("2");
        assertThat(awaiting(dashboard(world.dan()))).as("not counting their own").isEqualTo("1");
        assertThat(awaiting(dashboard(world.carol()))).isEqualTo("null");

        transition(world.alice(), carols, "APPROVE", null);

        assertThat(awaiting(dashboard(world.alice()))).isEqualTo("1");
        assertThat(awaiting(dashboard(world.dan()))).isEqualTo("0");
    }

    @Test
    void everyNumberMatchesTheRequestListForTheSameViewer() {
        draft(world.carol(), "Stays draft");
        submitted(world.carol(), "Stays submitted");
        Req toApprove = submitted(world.carol(), "Gets approved");
        Req toReject = submitted(world.frank(), "Gets rejected");
        Req toRevise = submitted(world.frank(), "Gets revised");
        draft(world.dan(), "Dan draft");
        submitted(world.dan(), "Dan submission");
        transition(world.ann(), toApprove, "APPROVE", null);
        transition(world.ann(), toReject, "REJECT", "No budget");
        transition(world.ann(), toRevise, "REQUEST_CHANGES", "Add detail");

        for (Member viewer :
                List.of(world.carol(), world.frank(), world.dan(), world.ann(), world.alice())) {
            String body = dashboard(viewer);
            Map<String, Long> overall = byStatusMaps(body).get(0);
            for (Map.Entry<String, Long> entry : overall.entrySet()) {
                assertThat(entry.getValue())
                        .as(viewer.email() + " " + entry.getKey())
                        .isEqualTo(listTotal(viewer, "?status=" + entry.getKey()));
            }
            assertThat(totals(body).get(0))
                    .as(viewer.email() + " total")
                    .isEqualTo(listTotal(viewer, ""));
            assertThat(totals(body).get(0))
                    .as("the total is the sum of the statuses")
                    .isEqualTo(overall.values().stream().mapToLong(Long::longValue).sum());
            assertThat(totals(body).get(1))
                    .as(viewer.email() + " mine")
                    .isEqualTo(listTotal(viewer, "?mine=true"));
            boolean reviewer = viewer != world.carol() && viewer != world.frank();
            assertThat(awaiting(body))
                    .as(viewer.email() + " awaiting")
                    .isEqualTo(
                            reviewer
                                    ? String.valueOf(listTotal(viewer, "?reviewable=true"))
                                    : "null");
        }
    }

    @Test
    void requestsOfOtherOrganizationsNeverCount() {
        Req bobs = draftIn(world.bob(), world.orgB(), "Bobs request");
        transitionIn(world.bob(), world.orgB(), bobs, "SUBMIT", null);

        String alice = dashboard(world.alice());
        String bob = world.bob().http().get("/api/orgs/" + world.orgB() + "/dashboard").body();

        assertThat(totals(alice)).containsExactly(0L, 0L);
        assertThat(alice).contains("\"recent\":[]");
        assertThat(totals(bob)).containsExactly(1L, 1L);
        assertThat(
                        world.alice()
                                .http()
                                .get("/api/orgs/" + world.orgB() + "/dashboard")
                                .statusCode())
                .isEqualTo(404);
    }

    @Test
    void recentActivityIsNewestFirstAndLimitedToEight() {
        List<Req> submissions = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            submissions.add(submitted(world.carol(), "Request " + i));
        }
        for (Req request : submissions) {
            transition(world.dan(), request, "APPROVE", null);
        }

        String body = dashboard(world.alice());

        assertThat(all(body, "action"))
                .containsExactly(
                        "APPROVE", "APPROVE", "APPROVE", "APPROVE", "APPROVE", "SUBMIT", "SUBMIT",
                        "SUBMIT");
        assertThat(all(body, "reference"))
                .containsExactly(
                        "REQ-000005",
                        "REQ-000004",
                        "REQ-000003",
                        "REQ-000002",
                        "REQ-000001",
                        "REQ-000005",
                        "REQ-000004",
                        "REQ-000003");
        assertThat(all(body, "actorName").subList(0, 5)).containsOnly("dan");
        assertThat(body).contains("\"toStatus\":\"APPROVED\"");
    }

    @Test
    void employeesRecentActivityOnlyCoversTheirOwnRequests() {
        Req carols = submitted(world.carol(), "Carol request");
        submitted(world.frank(), "Frank request");
        transition(world.dan(), carols, "APPROVE", null);

        String carol = dashboard(world.carol());
        String frank = dashboard(world.frank());

        assertThat(all(carol, "title")).containsExactly("Carol request", "Carol request");
        assertThat(all(carol, "action")).containsExactly("APPROVE", "SUBMIT");
        assertThat(all(frank, "title")).containsExactly("Frank request");
        assertThat(all(frank, "action")).containsExactly("SUBMIT");
    }

    @Test
    void recentActivityNeverCarriesCommentsOrEmailAddresses() {
        Req request = submitted(world.carol(), "Needs a decision");
        transition(world.dan(), request, "REJECT", "Top secret rationale");

        String body = dashboard(world.alice());

        assertThat(body)
                .contains("REJECT")
                .doesNotContain("Top secret")
                .doesNotContain("@")
                .doesNotContain("\"comment\"");
    }

    @Test
    void theIndexBehindRecentActivityExists() {
        int count =
                jdbc.sql(
                                "SELECT count(*) FROM pg_indexes WHERE indexname = 'ix_request_events_org_time'")
                        .query(Integer.class)
                        .single();

        assertThat(count).isEqualTo(1);
    }

    @Test
    void aRemovedMemberCanNoLongerOpenTheDashboard() {
        assertThat(dashboardResponse(world.dan()).statusCode()).isEqualTo(200);
        jdbc.sql(
                        "UPDATE memberships SET status = 'REVOKED' WHERE organization_id = ? AND user_id = ?")
                .params(world.orgA(), world.dan().userId())
                .update();

        assertThat(dashboardResponse(world.dan()).statusCode()).isEqualTo(404);
    }
}
