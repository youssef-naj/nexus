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
class RequestWorkflowIntegrationTest {

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

    private Req draftBy(Member as, String title) {
        return draftIn(as, world.orgA(), title);
    }

    private Req draftIn(Member as, UUID org, String title) {
        HttpResponse<String> response =
                as.http()
                        .postJson(
                                requests(org),
                                "{\"title\":\"" + title + "\",\"category\":\"HR\"}",
                                as.http().csrfToken());
        assertThat(response.statusCode()).as("creating " + title).isEqualTo(201);
        return new Req(field(response.body(), "id"), versionOf(response.body()));
    }

    private static String body(String action, long version, String comment) {
        return "{\"action\":\""
                + action
                + "\",\"version\":"
                + version
                + (comment == null ? "" : ",\"comment\":\"" + comment + "\"")
                + "}";
    }

    private HttpResponse<String> act(
            Member as, UUID org, String id, String action, long version, String comment) {
        return as.http()
                .postJson(
                        requests(org) + "/" + id + "/transitions",
                        body(action, version, comment),
                        as.http().csrfToken());
    }

    private HttpResponse<String> act(
            Member as, String id, String action, long version, String comment) {
        return act(as, world.orgA(), id, action, version, comment);
    }

    private Req submitted(Member creator) {
        Req draft = draftBy(creator, "Needs review");
        HttpResponse<String> response = act(creator, draft.id(), "SUBMIT", draft.version(), null);
        assertThat(response.statusCode()).isEqualTo(200);
        return new Req(draft.id(), versionOf(response.body()));
    }

    private HttpResponse<String> get(Member as, String id) {
        return as.http().get(requests(world.orgA()) + "/" + id);
    }

    private HttpResponse<String> events(Member as, UUID org, String id) {
        return as.http().get(requests(org) + "/" + id + "/events");
    }

    private HttpResponse<String> edit(Member as, String id, String title, long version) {
        return as.http()
                .request(
                        "PUT",
                        requests(world.orgA()) + "/" + id,
                        "{\"title\":\""
                                + title
                                + "\",\"category\":\"HR\",\"version\":"
                                + version
                                + "}",
                        as.http().csrfToken());
    }

    private static String field(String body, String name) {
        Matcher matcher = Pattern.compile("\"" + name + "\":\"([^\"]*)\"").matcher(body);
        assertThat(matcher.find()).as(name + " in " + body).isTrue();
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

    private static long versionOf(String body) {
        Matcher matcher = Pattern.compile("\"version\":(\\d+)").matcher(body);
        assertThat(matcher.find()).as("a version in " + body).isTrue();
        return Long.parseLong(matcher.group(1));
    }

    private int eventCount(String requestId, String action) {
        return jdbc.sql("SELECT count(*) FROM request_events WHERE request_id = ? AND action = ?")
                .params(UUID.fromString(requestId), action)
                .query(Integer.class)
                .single();
    }

    @Test
    void theTransitionEndpointIsInvisibleToEveryoneOutsideTheOrganization() {
        Req request = submitted(world.carol());

        CrossTenantAssertions.assertIsolated(
                world,
                (client, segment) ->
                        client.postJson(
                                "/api/orgs/"
                                        + segment
                                        + "/requests/"
                                        + request.id()
                                        + "/transitions",
                                body("APPROVE", 1, null),
                                client.csrfToken()));
    }

    @Test
    void theHistoryEndpointIsInvisibleToEveryoneOutsideTheOrganization() {
        Req request = submitted(world.carol());

        CrossTenantAssertions.assertIsolated(
                world,
                (client, segment) ->
                        client.get(
                                "/api/orgs/" + segment + "/requests/" + request.id() + "/events"));
    }

    @Test
    void aRequestIsSubmittedThenApprovedAndEveryStepIsInTheHistory() {
        HttpResponse<String> created =
                world.carol()
                        .http()
                        .postJson(
                                requests(world.orgA()),
                                "{\"title\":\"Laptop\",\"category\":\"IT_SUPPORT\"}",
                                world.carol().http().csrfToken());
        String id = field(created.body(), "id");
        assertThat(created.body()).contains("\"actions\":[\"SUBMIT\"]");

        HttpResponse<String> submit =
                act(world.carol(), id, "SUBMIT", 0, "Urgent, needed by Friday");
        assertThat(submit.statusCode()).isEqualTo(200);
        assertThat(submit.body())
                .contains("\"status\":\"SUBMITTED\"")
                .contains("\"version\":1")
                .contains("\"editable\":false")
                .contains("\"actions\":[]");
        assertThat(get(world.dan(), id).body())
                .contains("\"actions\":[\"APPROVE\",\"REJECT\",\"REQUEST_CHANGES\"]");

        HttpResponse<String> approved = act(world.dan(), id, "APPROVE", 1, "Looks good");
        assertThat(approved.statusCode()).isEqualTo(200);
        assertThat(approved.body())
                .contains("\"status\":\"APPROVED\"")
                .contains("\"version\":2")
                .contains("\"actions\":[]");

        HttpResponse<String> history = events(world.carol(), world.orgA(), id);
        assertThat(history.statusCode()).isEqualTo(200);
        assertThat(all(history.body(), "action")).containsExactly("SUBMIT", "APPROVE");
        assertThat(all(history.body(), "actorName")).containsExactly("carol", "dan");
        assertThat(all(history.body(), "comment"))
                .containsExactly("Urgent, needed by Friday", "Looks good");
        assertThat(all(history.body(), "fromStatus")).containsExactly("DRAFT", "SUBMITTED");
        assertThat(all(history.body(), "toStatus")).containsExactly("SUBMITTED", "APPROVED");
    }

    @Test
    void rejectingAndRequestingChangesRequireAComment() {
        Req request = submitted(world.carol());

        for (String action : List.of("REJECT", "REQUEST_CHANGES")) {
            HttpResponse<String> missing =
                    act(world.dan(), request.id(), action, request.version(), null);
            HttpResponse<String> blank =
                    act(world.dan(), request.id(), action, request.version(), "   ");
            assertThat(missing.statusCode()).as(action).isEqualTo(400);
            assertThat(missing.body()).contains("\"errors\"").contains("\"comment\"");
            assertThat(blank.statusCode()).as(action + " blank").isEqualTo(400);
        }
        assertThat(get(world.dan(), request.id()).body()).contains("\"status\":\"SUBMITTED\"");

        HttpResponse<String> rejected =
                act(world.dan(), request.id(), "REJECT", request.version(), "Out of budget");
        assertThat(rejected.statusCode()).isEqualTo(200);
        assertThat(rejected.body()).contains("\"status\":\"REJECTED\"");
        assertThat(all(events(world.carol(), world.orgA(), request.id()).body(), "comment"))
                .contains("Out of budget");
    }

    @Test
    void changesRequestedLetTheCreatorEditAndResubmit() {
        Req request = submitted(world.carol());

        HttpResponse<String> sentBack =
                act(
                        world.dan(),
                        request.id(),
                        "REQUEST_CHANGES",
                        request.version(),
                        "Add a budget");
        assertThat(sentBack.statusCode()).isEqualTo(200);
        assertThat(sentBack.body()).contains("\"status\":\"CHANGES_REQUESTED\"");
        HttpResponse<String> asCreator = get(world.carol(), request.id());
        assertThat(asCreator.body())
                .contains("\"editable\":true")
                .contains("\"actions\":[\"SUBMIT\"]");

        HttpResponse<String> edited =
                edit(
                        world.carol(),
                        request.id(),
                        "Laptop with budget",
                        versionOf(asCreator.body()));
        assertThat(edited.statusCode()).isEqualTo(200);
        HttpResponse<String> resubmitted =
                act(world.carol(), request.id(), "SUBMIT", versionOf(edited.body()), null);
        assertThat(resubmitted.statusCode()).isEqualTo(200);
        assertThat(resubmitted.body()).contains("\"status\":\"SUBMITTED\"");

        HttpResponse<String> approved =
                act(world.ann(), request.id(), "APPROVE", versionOf(resubmitted.body()), null);
        assertThat(approved.statusCode()).isEqualTo(200);
        assertThat(all(events(world.alice(), world.orgA(), request.id()).body(), "action"))
                .containsExactly("SUBMIT", "REQUEST_CHANGES", "SUBMIT", "APPROVE");
    }

    @Test
    void nobodyCanReviewTheirOwnRequestWhateverTheirRole() {
        for (Member reviewer : List.of(world.dan(), world.ann(), world.alice())) {
            Req own = submitted(reviewer);
            for (String action : List.of("APPROVE", "REJECT", "REQUEST_CHANGES")) {
                HttpResponse<String> response =
                        act(reviewer, own.id(), action, own.version(), "no");
                assertThat(response.statusCode())
                        .as(reviewer.email() + " " + action)
                        .isEqualTo(403);
                assertThat(response.body()).contains("SELF_REVIEW_NOT_ALLOWED");
            }
            assertThat(get(reviewer, own.id()).body())
                    .contains("\"status\":\"SUBMITTED\"")
                    .contains("\"actions\":[]");
        }
        Req dans = submitted(world.dan());
        assertThat(act(world.alice(), dans.id(), "APPROVE", dans.version(), null).statusCode())
                .as("someone else can review it")
                .isEqualTo(200);
    }

    @Test
    void employeesCannotReviewAnything() {
        Req carols = submitted(world.carol());
        Req franks = submitted(world.frank());

        assertThat(act(world.frank(), carols.id(), "APPROVE", carols.version(), null).statusCode())
                .as("an employee cannot even see someone else's request")
                .isEqualTo(404);
        assertThat(act(world.carol(), carols.id(), "APPROVE", carols.version(), null).statusCode())
                .as("an employee approving their own request")
                .isEqualTo(403);
        assertThat(act(world.frank(), franks.id(), "APPROVE", franks.version(), null).statusCode())
                .isEqualTo(403);
        assertThat(get(world.dan(), carols.id()).body()).contains("\"status\":\"SUBMITTED\"");
    }

    @Test
    void onlyTheCreatorCanSubmit() {
        Req draft = draftBy(world.carol(), "Mine");

        HttpResponse<String> byManager =
                act(world.dan(), draft.id(), "SUBMIT", draft.version(), null);
        HttpResponse<String> byOwner =
                act(world.alice(), draft.id(), "SUBMIT", draft.version(), null);

        assertThat(byManager.statusCode()).isEqualTo(403);
        assertThat(byManager.body()).contains("NOT_REQUEST_OWNER");
        assertThat(byOwner.statusCode()).isEqualTo(403);
        assertThat(act(world.frank(), draft.id(), "SUBMIT", draft.version(), null).statusCode())
                .isEqualTo(404);
        assertThat(get(world.carol(), draft.id()).body()).contains("\"status\":\"DRAFT\"");
    }

    @Test
    void impossibleTransitionsAreConflicts() {
        Req draft = draftBy(world.carol(), "Not yet submitted");

        HttpResponse<String> approveDraft =
                act(world.dan(), draft.id(), "APPROVE", draft.version(), null);
        assertThat(approveDraft.statusCode()).isEqualTo(409);
        assertThat(approveDraft.body()).contains("INVALID_TRANSITION");
        assertThat(act(world.dan(), draft.id(), "REJECT", draft.version(), "no").statusCode())
                .isEqualTo(409);

        HttpResponse<String> submitted =
                act(world.carol(), draft.id(), "SUBMIT", draft.version(), null);
        long version = versionOf(submitted.body());
        assertThat(act(world.carol(), draft.id(), "SUBMIT", version, null).statusCode())
                .as("submitting twice")
                .isEqualTo(409);

        HttpResponse<String> approved = act(world.dan(), draft.id(), "APPROVE", version, null);
        long finalVersion = versionOf(approved.body());
        for (String action : List.of("APPROVE", "REJECT", "REQUEST_CHANGES")) {
            assertThat(act(world.ann(), draft.id(), action, finalVersion, "again").statusCode())
                    .as(action + " after approval")
                    .isEqualTo(409);
        }
        assertThat(act(world.carol(), draft.id(), "SUBMIT", finalVersion, null).statusCode())
                .isEqualTo(409);
    }

    @Test
    void reviewersCanOnlyDecideWhatTheyActuallySaw() {
        Req request = submitted(world.carol()); // version 1
        long seenByAnn = request.version();

        HttpResponse<String> sentBack =
                act(
                        world.dan(),
                        request.id(),
                        "REQUEST_CHANGES",
                        request.version(),
                        "Please revise");
        HttpResponse<String> edited =
                edit(world.carol(), request.id(), "Revised", versionOf(sentBack.body()));
        HttpResponse<String> resubmitted =
                act(world.carol(), request.id(), "SUBMIT", versionOf(edited.body()), null);

        HttpResponse<String> stale = act(world.ann(), request.id(), "APPROVE", seenByAnn, null);
        assertThat(stale.statusCode()).isEqualTo(409);
        assertThat(stale.body()).contains("STALE_VERSION");
        assertThat(
                        act(
                                        world.ann(),
                                        request.id(),
                                        "APPROVE",
                                        versionOf(resubmitted.body()),
                                        null)
                                .statusCode())
                .isEqualTo(200);
    }

    @Test
    void commentsAreValidated() {
        Req draft = draftBy(world.carol(), "Comments");

        HttpResponse<String> tooLong =
                act(world.carol(), draft.id(), "SUBMIT", draft.version(), "c".repeat(1001));
        HttpResponse<String> control =
                act(world.carol(), draft.id(), "SUBMIT", draft.version(), "bad\\u0007comment");

        assertThat(tooLong.statusCode()).isEqualTo(400);
        assertThat(tooLong.body()).contains("\"comment\"");
        assertThat(control.statusCode()).isEqualTo(400);
        assertThat(get(world.carol(), draft.id()).body()).contains("\"status\":\"DRAFT\"");
    }

    @Test
    void unknownActionsAndMissingFieldsAreRejected() {
        Req draft = draftBy(world.carol(), "Bad calls");
        var carol = world.carol().http();
        String path = requests(world.orgA()) + "/" + draft.id() + "/transitions";

        HttpResponse<String> unknownAction =
                carol.postJson(path, "{\"action\":\"DELETE\",\"version\":0}", carol.csrfToken());
        HttpResponse<String> noVersion =
                carol.postJson(path, "{\"action\":\"SUBMIT\"}", carol.csrfToken());
        HttpResponse<String> noAction = carol.postJson(path, "{\"version\":0}", carol.csrfToken());
        HttpResponse<String> noCsrf = carol.postJson(path, body("SUBMIT", 0, null), null);

        assertThat(unknownAction.statusCode()).isEqualTo(400);
        assertThat(noVersion.statusCode()).isEqualTo(400);
        assertThat(noVersion.body()).contains("\"version\"");
        assertThat(noAction.statusCode()).isEqualTo(400);
        assertThat(noCsrf.statusCode()).isEqualTo(403);
    }

    @Test
    void twoReviewersApprovingAtOnceProduceExactlyOneWinner() throws Exception {
        Req request = submitted(world.carol());
        String danCsrf = world.dan().http().csrfToken();
        String annCsrf = world.ann().http().csrfToken();
        String path = requests(world.orgA()) + "/" + request.id() + "/transitions";

        List<Integer> statuses =
                Parallel.statuses(
                        List.of(
                                () ->
                                        world.dan()
                                                .http()
                                                .postJson(
                                                        path,
                                                        body("APPROVE", request.version(), null),
                                                        danCsrf),
                                () ->
                                        world.ann()
                                                .http()
                                                .postJson(
                                                        path,
                                                        body("APPROVE", request.version(), null),
                                                        annCsrf)));

        assertThat(statuses).containsExactly(200, 409);
        assertThat(eventCount(request.id(), "APPROVE")).isEqualTo(1);
    }

    @Test
    void anApprovalAndARejectionAtOnceProduceExactlyOneDecision() throws Exception {
        Req request = submitted(world.carol());
        String danCsrf = world.dan().http().csrfToken();
        String annCsrf = world.ann().http().csrfToken();
        String path = requests(world.orgA()) + "/" + request.id() + "/transitions";

        List<Integer> statuses =
                Parallel.statuses(
                        List.of(
                                () ->
                                        world.dan()
                                                .http()
                                                .postJson(
                                                        path,
                                                        body("APPROVE", request.version(), null),
                                                        danCsrf),
                                () ->
                                        world.ann()
                                                .http()
                                                .postJson(
                                                        path,
                                                        body(
                                                                "REJECT",
                                                                request.version(),
                                                                "Too expensive"),
                                                        annCsrf)));

        assertThat(statuses).containsExactly(200, 409);
        int events =
                jdbc.sql("SELECT count(*) FROM request_events WHERE request_id = ?")
                        .param(UUID.fromString(request.id()))
                        .query(Integer.class)
                        .single();
        assertThat(events).as("one submission and one decision").isEqualTo(2);
    }

    @Test
    void requestIdsFromAnotherOrganizationAreNotFound() {
        Req bobs = draftIn(world.bob(), world.orgB(), "Bobs secret");
        HttpResponse<String> submit =
                act(world.bob(), world.orgB(), bobs.id(), "SUBMIT", bobs.version(), null);
        assertThat(submit.statusCode()).isEqualTo(200);

        assertThat(act(world.alice(), world.orgA(), bobs.id(), "APPROVE", 1, null).statusCode())
                .isEqualTo(404);
        assertThat(act(world.alice(), world.orgB(), bobs.id(), "APPROVE", 1, null).statusCode())
                .isEqualTo(404);
        assertThat(events(world.alice(), world.orgA(), bobs.id()).statusCode()).isEqualTo(404);
        assertThat(events(world.alice(), world.orgB(), bobs.id()).statusCode()).isEqualTo(404);
        assertThat(eventCount(bobs.id(), "APPROVE")).isZero();
    }

    @Test
    void theHistoryIsVisibleToThoseWhoCanSeeTheRequest() {
        Req request = submitted(world.carol());

        assertThat(events(world.carol(), world.orgA(), request.id()).statusCode()).isEqualTo(200);
        assertThat(events(world.dan(), world.orgA(), request.id()).statusCode()).isEqualTo(200);
        assertThat(events(world.frank(), world.orgA(), request.id()).statusCode()).isEqualTo(404);
        assertThat(events(world.carol(), world.orgA(), UUID.randomUUID().toString()).statusCode())
                .isEqualTo(404);
    }

    @Test
    void auditRowsNameTheRequestButNeverCarryTheComment() {
        Req request = submitted(world.carol());
        act(world.dan(), request.id(), "APPROVE", request.version(), "Secret rationale");

        List<String> types =
                jdbc.sql(
                                "SELECT event_type FROM audit_logs WHERE target_id = ? ORDER BY occurred_at, id")
                        .param(UUID.fromString(request.id()))
                        .query(String.class)
                        .list();
        int leaked =
                jdbc.sql(
                                "SELECT count(*) FROM audit_logs WHERE target_id = ? AND metadata::text LIKE '%Secret%'")
                        .param(UUID.fromString(request.id()))
                        .query(Integer.class)
                        .single();
        String reference =
                jdbc.sql(
                                "SELECT metadata ->> 'reference' FROM audit_logs WHERE target_id = ? AND event_type = 'REQUEST_APPROVED'")
                        .param(UUID.fromString(request.id()))
                        .query(String.class)
                        .single();

        assertThat(types).containsExactly("REQUEST_SUBMITTED", "REQUEST_APPROVED");
        assertThat(leaked).isZero();
        assertThat(reference).matches("REQ-\\d{6}");
    }

    @Test
    void aReviewerWhoWasRemovedCanNoLongerDecide() {
        Req request = submitted(world.carol());
        jdbc.sql(
                        "UPDATE memberships SET status = 'REVOKED' WHERE organization_id = ? AND user_id = ?")
                .params(world.orgA(), world.dan().userId())
                .update();

        assertThat(act(world.dan(), request.id(), "APPROVE", request.version(), null).statusCode())
                .isEqualTo(404);
        assertThat(act(world.ann(), request.id(), "APPROVE", request.version(), null).statusCode())
                .isEqualTo(200);
    }
}
