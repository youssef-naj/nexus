package com.l2c.nexus.request.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.l2c.nexus.TestcontainersConfiguration;
import com.l2c.nexus.support.CrossTenantAssertions;
import com.l2c.nexus.support.Parallel;
import com.l2c.nexus.support.TenantWorld;
import com.l2c.nexus.support.TenantWorld.Member;
import com.l2c.nexus.support.TenantWorldFactory;
import java.net.http.HttpResponse;
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
class RequestAssignmentIntegrationTest {

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
        assertThat(response.statusCode()).isEqualTo(201);
        return new Req(
                first(response.body(), "\"id\":\"([0-9a-f-]{36})\""), versionOf(response.body()));
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
        return new Req(request.id(), versionOf(response.body()));
    }

    private Req submitted(Member as, String title) {
        return transitionIn(as, world.orgA(), draftIn(as, world.orgA(), title), "SUBMIT", null);
    }

    private HttpResponse<String> assign(Member as, String requestId, UUID assignee, long version) {
        return as.http()
                .request(
                        "PUT",
                        requests(world.orgA()) + "/" + requestId + "/assignee",
                        "{\"membershipId\":\"" + assignee + "\",\"version\":" + version + "}",
                        as.http().csrfToken());
    }

    private HttpResponse<String> unassign(Member as, String requestId, long version) {
        return as.http()
                .request(
                        "DELETE",
                        requests(world.orgA()) + "/" + requestId + "/assignee?version=" + version,
                        null,
                        as.http().csrfToken());
    }

    private HttpResponse<String> get(Member as, String id) {
        return as.http().get(requests(world.orgA()) + "/" + id);
    }

    private HttpResponse<String> events(Member as, String id) {
        return as.http().get(requests(world.orgA()) + "/" + id + "/events");
    }

    private HttpResponse<String> list(Member as, String query) {
        return as.http().get(requests(world.orgA()) + query);
    }

    private UUID membershipOf(Member member) {
        return jdbc.sql("SELECT id FROM memberships WHERE organization_id = ? AND user_id = ?")
                .params(world.orgA(), member.userId())
                .query(UUID.class)
                .single();
    }

    private boolean unassigned(String requestId) {
        int count =
                jdbc.sql(
                                "SELECT count(*) FROM service_requests WHERE id = ? AND assignee_membership_id IS NULL")
                        .param(UUID.fromString(requestId))
                        .query(Integer.class)
                        .single();
        return count == 1;
    }

    private HttpResponse<String> removeMember(Member as, Member target) {
        return as.http()
                .request(
                        "DELETE",
                        "/api/orgs/" + world.orgA() + "/members/" + membershipOf(target),
                        null,
                        as.http().csrfToken());
    }

    private static String first(String body, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(body);
        assertThat(matcher.find()).as(regex + " in " + body).isTrue();
        return matcher.group(1);
    }

    private static long versionOf(String body) {
        return Long.parseLong(first(body, "\"version\":(\\d+)"));
    }

    private static List<String> all(String body, String name) {
        Matcher matcher = Pattern.compile("\"" + name + "\":\"([^\"]*)\"").matcher(body);
        List<String> values = new ArrayList<>();
        while (matcher.find()) {
            values.add(matcher.group(1));
        }
        return values;
    }

    private String dashboardAssigned(Member as) {
        return first(
                as.http().get("/api/orgs/" + world.orgA() + "/dashboard").body(),
                "\"assignedToMe\":(null|\\d+)");
    }

    @Test
    void assigningAndUnassigningAreInvisibleToEveryoneOutsideTheOrganization() {
        Req request = submitted(world.carol(), "Private");
        UUID dan = membershipOf(world.dan());

        CrossTenantAssertions.assertIsolated(
                world,
                (client, segment) ->
                        client.request(
                                "PUT",
                                "/api/orgs/" + segment + "/requests/" + request.id() + "/assignee",
                                "{\"membershipId\":\"" + dan + "\",\"version\":1}",
                                client.csrfToken()));
        CrossTenantAssertions.assertIsolated(
                world,
                (client, segment) ->
                        client.request(
                                "DELETE",
                                "/api/orgs/"
                                        + segment
                                        + "/requests/"
                                        + request.id()
                                        + "/assignee?version=1",
                                null,
                                client.csrfToken()));
    }

    @Test
    void theReviewerListIsInvisibleToEveryoneOutsideTheOrganization() {
        CrossTenantAssertions.assertIsolated(
                world, (client, segment) -> client.get("/api/orgs/" + segment + "/reviewers"));
    }

    @Test
    void theReviewerListHoldsOnlyActiveReviewersSortedByNameAndIsRefusedToEmployees() {
        jdbc.sql("UPDATE memberships SET status = 'REVOKED' WHERE id = ?")
                .param(membershipOf(world.dan()))
                .update();

        HttpResponse<String> response =
                world.alice().http().get("/api/orgs/" + world.orgA() + "/reviewers");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(all(response.body(), "displayName")).containsExactly("alice", "ann");
        assertThat(response.body()).contains("\"you\":true").contains("\"you\":false");
        assertThat(
                        world.carol()
                                .http()
                                .get("/api/orgs/" + world.orgA() + "/reviewers")
                                .statusCode())
                .isEqualTo(403);
    }

    @Test
    void aReviewerAssignsASubmittedRequestAndItShowsInTheDetailHistoryAndAudit() {
        Req request = submitted(world.carol(), "Needs an owner");
        UUID ann = membershipOf(world.ann());

        HttpResponse<String> response = assign(world.dan(), request.id(), ann, request.version());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("\"assigneeName\":\"ann\"")
                .contains("\"version\":2")
                .contains("\"assignable\":true");
        HttpResponse<String> history = events(world.carol(), request.id());
        assertThat(all(history.body(), "action")).containsExactly("SUBMIT", "ASSIGN");
        assertThat(all(history.body(), "actorName")).containsExactly("carol", "dan");
        assertThat(history.body()).contains("\"targetName\":\"ann\"");
        String audited =
                jdbc.sql(
                                "SELECT metadata ->> 'assigneeMembershipId' FROM audit_logs WHERE event_type = 'REQUEST_ASSIGNED' AND target_id = ?")
                        .param(UUID.fromString(request.id()))
                        .query(String.class)
                        .single();
        assertThat(audited).isEqualTo(ann.toString());
    }

    @Test
    void reviewersCanAssignRequestsToThemselves() {
        Req request = submitted(world.carol(), "Mine now");

        HttpResponse<String> response =
                assign(world.dan(), request.id(), membershipOf(world.dan()), request.version());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"assigneeName\":\"dan\"");
    }

    @Test
    void employeesCannotAssignAndOnlyReviewersAreToldTheyMay() {
        Req request = submitted(world.carol(), "Carols request");
        UUID dan = membershipOf(world.dan());

        assertThat(assign(world.carol(), request.id(), dan, request.version()).statusCode())
                .as("the creator")
                .isEqualTo(403);
        assertThat(assign(world.frank(), request.id(), dan, request.version()).statusCode())
                .as("someone who cannot see it")
                .isEqualTo(404);
        assertThat(unassign(world.carol(), request.id(), request.version()).statusCode())
                .isEqualTo(403);
        assertThat(get(world.carol(), request.id()).body()).contains("\"assignable\":false");
        assertThat(get(world.dan(), request.id()).body()).contains("\"assignable\":true");
        assertThat(unassigned(request.id())).isTrue();
    }

    @Test
    void onlySubmittedRequestsCanBeAssigned() {
        Req draft = draftIn(world.carol(), world.orgA(), "Not yet");
        Req done = submitted(world.carol(), "Decided");
        Req approved = transitionIn(world.dan(), world.orgA(), done, "APPROVE", null);
        UUID ann = membershipOf(world.ann());

        HttpResponse<String> onDraft = assign(world.dan(), draft.id(), ann, draft.version());
        HttpResponse<String> onApproved =
                assign(world.dan(), approved.id(), ann, approved.version());

        assertThat(onDraft.statusCode()).isEqualTo(409);
        assertThat(onDraft.body()).contains("REQUEST_NOT_ASSIGNABLE");
        assertThat(onApproved.statusCode()).isEqualTo(409);
        assertThat(get(world.dan(), draft.id()).body()).contains("\"assignable\":false");
    }

    @Test
    void theAssigneeMustBeAnActiveReviewerAndNotTheCreatorAndEveryFailureLooksTheSame() {
        Req request = submitted(world.dan(), "Dans request");
        UUID bobsMembership =
                jdbc.sql("SELECT id FROM memberships WHERE organization_id = ? AND user_id = ?")
                        .params(world.orgB(), world.bob().userId())
                        .query(UUID.class)
                        .single();
        jdbc.sql("UPDATE memberships SET status = 'REVOKED' WHERE id = ?")
                .param(membershipOf(world.ann()))
                .update();

        HttpResponse<String> employee =
                assign(world.alice(), request.id(), membershipOf(world.carol()), request.version());
        HttpResponse<String> creator =
                assign(world.alice(), request.id(), membershipOf(world.dan()), request.version());
        HttpResponse<String> unknown =
                assign(world.alice(), request.id(), UUID.randomUUID(), request.version());
        HttpResponse<String> foreign =
                assign(world.alice(), request.id(), bobsMembership, request.version());
        HttpResponse<String> revoked =
                assign(world.alice(), request.id(), membershipOf(world.ann()), request.version());

        for (HttpResponse<String> response :
                List.of(employee, creator, unknown, foreign, revoked)) {
            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(response.body()).contains("\"errors\"").contains("\"membershipId\"");
        }
        assertThat(foreign.body()).isEqualTo(unknown.body());
        assertThat(revoked.body()).isEqualTo(employee.body());
        assertThat(unassigned(request.id())).isTrue();
    }

    @Test
    void aStaleVersionIsRejected() {
        Req request = submitted(world.carol(), "Moves on");

        HttpResponse<String> response =
                assign(world.dan(), request.id(), membershipOf(world.ann()), request.version() - 1);

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.body()).contains("STALE_VERSION");
    }

    @Test
    void reassigningAndUnassigningWorkAndNoOpsLeaveNoTrace() {
        Req request = submitted(world.carol(), "Passed around");
        UUID ann = membershipOf(world.ann());
        UUID dan = membershipOf(world.dan());

        long v = versionOf(assign(world.alice(), request.id(), ann, request.version()).body());
        HttpResponse<String> same = assign(world.alice(), request.id(), ann, v);
        assertThat(versionOf(same.body()))
                .as("assigning the same person changes nothing")
                .isEqualTo(v);
        v = versionOf(assign(world.alice(), request.id(), dan, v).body());
        HttpResponse<String> removed = unassign(world.alice(), request.id(), v);
        assertThat(removed.statusCode()).isEqualTo(200);
        assertThat(removed.body()).contains("\"assigneeName\":null");
        long afterRemoval = versionOf(removed.body());
        HttpResponse<String> again = unassign(world.alice(), request.id(), afterRemoval);

        assertThat(again.statusCode()).isEqualTo(200);
        assertThat(versionOf(again.body())).isEqualTo(afterRemoval);
        assertThat(all(events(world.carol(), request.id()).body(), "action"))
                .containsExactly("SUBMIT", "ASSIGN", "ASSIGN", "UNASSIGN");
        assertThat(all(events(world.carol(), request.id()).body(), "targetName"))
                .containsExactly("ann", "dan", "dan");
    }

    @Test
    void assignmentDoesNotBlockDecisionsButChangesTheVersionTheReviewerSaw() {
        Req request = submitted(world.carol(), "Advisory only");
        long assignedVersion =
                versionOf(
                        assign(
                                        world.alice(),
                                        request.id(),
                                        membershipOf(world.dan()),
                                        request.version())
                                .body());
        String body = "{\"action\":\"APPROVE\",\"version\":%d}";
        var ann = world.ann().http();
        String path = requests(world.orgA()) + "/" + request.id() + "/transitions";

        HttpResponse<String> stale =
                ann.postJson(path, body.formatted(request.version()), ann.csrfToken());
        HttpResponse<String> byAnotherReviewer =
                ann.postJson(path, body.formatted(assignedVersion), ann.csrfToken());

        assertThat(stale.statusCode()).isEqualTo(409);
        assertThat(stale.body()).contains("STALE_VERSION");
        assertThat(byAnotherReviewer.statusCode())
                .as("not only the assignee may decide")
                .isEqualTo(200);
    }

    @Test
    void assignedRequestsShowInTheAssigneesQueueAndDashboardUntilTheyAreDecided() {
        Req request = submitted(world.carol(), "In my queue");
        assign(world.alice(), request.id(), membershipOf(world.dan()), request.version());

        assertThat(all(list(world.dan(), "?assignedToMe=true").body(), "title"))
                .containsExactly("In my queue");
        assertThat(dashboardAssigned(world.dan())).isEqualTo("1");
        assertThat(list(world.ann(), "?assignedToMe=true").body()).contains("\"totalElements\":0");
        assertThat(dashboardAssigned(world.ann())).isEqualTo("0");
        assertThat(dashboardAssigned(world.carol())).isEqualTo("null");
        assertThat(list(world.carol(), "?assignedToMe=true").body())
                .contains("\"totalElements\":0");

        transitionIn(world.ann(), world.orgA(), new Req(request.id(), 2), "APPROVE", null);

        assertThat(list(world.dan(), "?assignedToMe=true").body()).contains("\"totalElements\":0");
        assertThat(dashboardAssigned(world.dan())).isEqualTo("0");
    }

    @Test
    void theAssignedFilterWithAConflictingStatusIsEmpty() {
        Req request = submitted(world.carol(), "Queue");
        assign(world.alice(), request.id(), membershipOf(world.dan()), request.version());

        assertThat(list(world.dan(), "?assignedToMe=true&status=APPROVED").body())
                .contains("\"totalElements\":0");
        assertThat(list(world.dan(), "?assignedToMe=true&status=SUBMITTED").body())
                .contains("\"totalElements\":1");
    }

    @Test
    void removingAReviewerClearsTheirOpenAssignmentsButDecidedRequestsKeepTheRecord() {
        Req open = submitted(world.carol(), "Still open");
        Req decided = submitted(world.carol(), "Already handled");
        assign(world.alice(), open.id(), membershipOf(world.dan()), open.version());
        long decidedVersion =
                versionOf(
                        assign(
                                        world.alice(),
                                        decided.id(),
                                        membershipOf(world.dan()),
                                        decided.version())
                                .body());
        transitionIn(
                world.ann(), world.orgA(), new Req(decided.id(), decidedVersion), "APPROVE", null);
        long versionBefore = versionOf(get(world.alice(), open.id()).body());

        assertThat(removeMember(world.ann(), world.dan()).statusCode()).isEqualTo(204);

        assertThat(unassigned(open.id())).isTrue();
        assertThat(versionOf(get(world.alice(), open.id()).body())).isGreaterThan(versionBefore);
        assertThat(unassigned(decided.id())).as("the record of who handled it stays").isFalse();
    }

    @Test
    void demotingAReviewerBelowReviewerClearsAssignmentsButStayingAReviewerDoesNot() {
        Req request = submitted(world.carol(), "Demotion test");
        assign(world.alice(), request.id(), membershipOf(world.dan()), request.version());
        UUID dan = membershipOf(world.dan());
        long memberVersion =
                jdbc.sql("SELECT version FROM memberships WHERE id = ?")
                        .param(dan)
                        .query(Long.class)
                        .single();
        var alice = world.alice().http();
        String path = "/api/orgs/" + world.orgA() + "/members/" + dan;

        HttpResponse<String> promoted =
                alice.request(
                        "PATCH",
                        path,
                        "{\"role\":\"ADMIN\",\"version\":" + memberVersion + "}",
                        alice.csrfToken());
        assertThat(promoted.statusCode()).isEqualTo(200);
        assertThat(unassigned(request.id())).as("still a reviewer").isFalse();

        HttpResponse<String> demoted =
                alice.request(
                        "PATCH",
                        path,
                        "{\"role\":\"EMPLOYEE\",\"version\":" + versionOf(promoted.body()) + "}",
                        alice.csrfToken());
        assertThat(demoted.statusCode()).isEqualTo(200);
        assertThat(unassigned(request.id())).as("no longer a reviewer").isTrue();
    }

    @Test
    void aReviewerWhoLeavesLosesTheirAssignments() {
        Req request = submitted(world.carol(), "Leaving soon");
        assign(world.alice(), request.id(), membershipOf(world.dan()), request.version());

        HttpResponse<String> left =
                world.dan()
                        .http()
                        .request(
                                "POST",
                                "/api/orgs/" + world.orgA() + "/leave",
                                null,
                                world.dan().http().csrfToken());

        assertThat(left.statusCode()).isEqualTo(204);
        assertThat(unassigned(request.id())).isTrue();
    }

    @Test
    void twoReviewersAssigningAtOnceProduceExactlyOneWinner() throws Exception {
        Req request = submitted(world.carol(), "Contested");
        UUID dan = membershipOf(world.dan());
        UUID alice = membershipOf(world.alice());
        String aliceCsrf = world.alice().http().csrfToken();
        String annCsrf = world.ann().http().csrfToken();
        String path = requests(world.orgA()) + "/" + request.id() + "/assignee";

        List<Integer> statuses =
                Parallel.statuses(
                        List.of(
                                () ->
                                        world.alice()
                                                .http()
                                                .request(
                                                        "PUT",
                                                        path,
                                                        "{\"membershipId\":\""
                                                                + dan
                                                                + "\",\"version\":"
                                                                + request.version()
                                                                + "}",
                                                        aliceCsrf),
                                () ->
                                        world.ann()
                                                .http()
                                                .request(
                                                        "PUT",
                                                        path,
                                                        "{\"membershipId\":\""
                                                                + alice
                                                                + "\",\"version\":"
                                                                + request.version()
                                                                + "}",
                                                        annCsrf)));

        assertThat(statuses).containsExactly(200, 409);
    }

    @Test
    void assigningWhileRemovingTheAssigneeNeverLeavesADanglingAssignment() throws Exception {
        Req request = submitted(world.carol(), "Race");
        UUID dan = membershipOf(world.dan());
        String aliceCsrf = world.alice().http().csrfToken();
        String annCsrf = world.ann().http().csrfToken();
        String path = requests(world.orgA()) + "/" + request.id() + "/assignee";

        List<Integer> statuses =
                Parallel.statuses(
                        List.of(
                                () ->
                                        world.alice()
                                                .http()
                                                .request(
                                                        "PUT",
                                                        path,
                                                        "{\"membershipId\":\""
                                                                + dan
                                                                + "\",\"version\":"
                                                                + request.version()
                                                                + "}",
                                                        aliceCsrf),
                                () ->
                                        world.ann()
                                                .http()
                                                .request(
                                                        "DELETE",
                                                        "/api/orgs/"
                                                                + world.orgA()
                                                                + "/members/"
                                                                + dan,
                                                        null,
                                                        annCsrf)));

        // Either order is valid, but a removed member never stays assigned
        assertThat(statuses).isIn(List.of(200, 204), List.of(204, 400));
        assertThat(unassigned(request.id())).isTrue();
    }

    @Test
    void requestIdsFromAnotherOrganizationAreNotFound() {
        Req bobs = draftIn(world.bob(), world.orgB(), "Bobs request");
        bobs = transitionIn(world.bob(), world.orgB(), bobs, "SUBMIT", null);
        UUID alice = membershipOf(world.alice());

        HttpResponse<String> viaOwnPath = assign(world.alice(), bobs.id(), alice, bobs.version());
        HttpResponse<String> viaTheirPath =
                world.alice()
                        .http()
                        .request(
                                "PUT",
                                requests(world.orgB()) + "/" + bobs.id() + "/assignee",
                                "{\"membershipId\":\""
                                        + alice
                                        + "\",\"version\":"
                                        + bobs.version()
                                        + "}",
                                world.alice().http().csrfToken());
        HttpResponse<String> unassignViaOwnPath =
                unassign(world.alice(), bobs.id(), bobs.version());

        assertThat(viaOwnPath.statusCode()).isEqualTo(404);
        assertThat(viaTheirPath.statusCode()).isEqualTo(404);
        assertThat(unassignViaOwnPath.statusCode()).isEqualTo(404);
        assertThat(unassigned(bobs.id())).isTrue();
    }
}
