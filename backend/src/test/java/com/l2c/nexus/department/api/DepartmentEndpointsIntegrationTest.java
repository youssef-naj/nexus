package com.l2c.nexus.department.api;

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
class DepartmentEndpointsIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired private TenantWorldFactory factory;
    @Autowired private JdbcClient jdbc;

    private TenantWorld world;

    @BeforeEach
    void setUp() {
        world = factory.create(port);
    }

    private static String base(UUID org) {
        return "/api/orgs/" + org + "/departments";
    }

    private HttpResponse<String> createRaw(Member as, UUID org, String json) {
        return as.http().postJson(base(org), json, as.http().csrfToken());
    }

    private HttpResponse<String> create(Member as, UUID org, String name) {
        return createRaw(as, org, "{\"name\":\"" + name + "\"}");
    }

    private String createdId(Member as, UUID org, String name) {
        HttpResponse<String> response = create(as, org, name);
        assertThat(response.statusCode()).as("creating " + name).isEqualTo(201);
        return idOf(response.body());
    }

    private HttpResponse<String> update(
            Member as,
            UUID org,
            String id,
            String name,
            String description,
            long version,
            String csrf) {
        String json =
                "{\"name\":\""
                        + name
                        + "\",\"description\":"
                        + (description == null ? "null" : "\"" + description + "\"")
                        + ",\"version\":"
                        + version
                        + "}";
        return as.http().request("PUT", base(org) + "/" + id, json, csrf);
    }

    private HttpResponse<String> update(
            Member as, UUID org, String id, String name, String description, long version) {
        return update(as, org, id, name, description, version, as.http().csrfToken());
    }

    private HttpResponse<String> action(Member as, UUID org, String id, String action) {
        return as.http()
                .request("POST", base(org) + "/" + id + "/" + action, null, as.http().csrfToken());
    }

    private HttpResponse<String> list(Member as, String query) {
        return as.http().get(base(world.orgA()) + query);
    }

    private static String idOf(String body) {
        Matcher matcher = Pattern.compile("\"id\":\"([0-9a-f-]{36})\"").matcher(body);
        assertThat(matcher.find()).as("an id in " + body).isTrue();
        return matcher.group(1);
    }

    private static long versionOf(String body) {
        Matcher matcher = Pattern.compile("\"version\":(\\d+)").matcher(body);
        assertThat(matcher.find()).as("a version in " + body).isTrue();
        return Long.parseLong(matcher.group(1));
    }

    private static List<String> names(String body) {
        Matcher matcher = Pattern.compile("\"name\":\"([^\"]*)\"").matcher(body);
        List<String> names = new ArrayList<>();
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private int auditCount(String eventType, String departmentId) {
        return jdbc.sql("SELECT count(*) FROM audit_logs WHERE event_type = ? AND target_id = ?")
                .params(eventType, UUID.fromString(departmentId))
                .query(Integer.class)
                .single();
    }

    @Test
    void theListIsInvisibleToEveryoneOutsideTheOrganization() {
        CrossTenantAssertions.assertIsolated(
                world, (client, segment) -> client.get("/api/orgs/" + segment + "/departments"));
    }

    @Test
    void creatingIsInvisibleToEveryoneOutsideTheOrganization() {
        CrossTenantAssertions.assertIsolated(
                world,
                (client, segment) ->
                        client.postJson(
                                "/api/orgs/" + segment + "/departments",
                                "{\"name\":\"Battery\"}",
                                client.csrfToken()));
    }

    @Test
    void anOwnerCreatesADepartmentWithANormalizedNameAndTheCreationIsAudited() {
        HttpResponse<String> response =
                createRaw(
                        world.alice(),
                        world.orgA(),
                        "{\"name\":\"  Platform    Engineering \",\"description\":\"  Builds things  \"}");

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.body())
                .contains("\"name\":\"Platform Engineering\"")
                .contains("\"description\":\"Builds things\"")
                .contains("\"active\":true")
                .contains("\"version\":0");
        String audited =
                jdbc.sql(
                                "SELECT metadata ->> 'name' FROM audit_logs WHERE event_type ="
                                        + " 'DEPARTMENT_CREATED' AND target_id = ? AND organization_id = ?")
                        .params(UUID.fromString(idOf(response.body())), world.orgA())
                        .query(String.class)
                        .single();
        assertThat(audited).isEqualTo("Platform Engineering");
    }

    @Test
    void anAdminCanCreateToo() {
        assertThat(create(world.ann(), world.orgA(), "Finance").statusCode()).isEqualTo(201);
    }

    @Test
    void everyMemberCanViewButOnlyOwnersAndAdminsCanManage() {
        String id = createdId(world.alice(), world.orgA(), "Operations");

        for (Member viewer : List.of(world.dan(), world.carol())) {
            assertThat(list(viewer, "").statusCode()).isEqualTo(200);
            assertThat(viewer.http().get(base(world.orgA()) + "/" + id).statusCode())
                    .isEqualTo(200);
            assertThat(create(viewer, world.orgA(), "Nope").statusCode()).isEqualTo(403);
            assertThat(update(viewer, world.orgA(), id, "Renamed", null, 0).statusCode())
                    .isEqualTo(403);
            assertThat(action(viewer, world.orgA(), id, "deactivate").statusCode()).isEqualTo(403);
        }
        // Nothing the refused callers tried has happened
        assertThat(world.alice().http().get(base(world.orgA()) + "/" + id).body())
                .contains("\"name\":\"Operations\"")
                .contains("\"active\":true");
    }

    @Test
    void duplicateNamesConflictIgnoringCaseButAreAllowedInAnotherOrganization() {
        createdId(world.alice(), world.orgA(), "Engineering");

        HttpResponse<String> duplicate = create(world.alice(), world.orgA(), "ENGINEERING");
        HttpResponse<String> otherOrganization = create(world.bob(), world.orgB(), "Engineering");

        assertThat(duplicate.statusCode()).isEqualTo(409);
        assertThat(duplicate.body()).contains("DEPARTMENT_NAME_TAKEN");
        assertThat(otherOrganization.statusCode()).isEqualTo(201);
    }

    @Test
    void invalidInputIsRejectedWithFieldErrors() {
        HttpResponse<String> blank = create(world.alice(), world.orgA(), "");
        HttpResponse<String> tooLong = create(world.alice(), world.orgA(), "x".repeat(81));
        HttpResponse<String> control = create(world.alice(), world.orgA(), "bad\\u0007name");
        HttpResponse<String> longDescription =
                createRaw(
                        world.alice(),
                        world.orgA(),
                        "{\"name\":\"Ok\",\"description\":\"" + "d".repeat(501) + "\"}");

        for (HttpResponse<String> response : List.of(blank, tooLong, control)) {
            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(response.body()).contains("\"errors\"").contains("\"name\"");
        }
        assertThat(longDescription.statusCode()).isEqualTo(400);
        assertThat(longDescription.body()).contains("\"description\"");
    }

    @Test
    void aDepartmentCanBeViewedByIdAndUnknownIdsAreNotFound() {
        String id = createdId(world.alice(), world.orgA(), "Legal");

        HttpResponse<String> found = world.carol().http().get(base(world.orgA()) + "/" + id);
        HttpResponse<String> missing =
                world.carol().http().get(base(world.orgA()) + "/" + UUID.randomUUID());

        assertThat(found.statusCode()).isEqualTo(200);
        assertThat(found.body()).contains("\"name\":\"Legal\"");
        assertThat(missing.statusCode()).isEqualTo(404);
    }

    @Test
    void updatingUsesTheVersionRejectsStaleChangesAndOnlyAuditsRealChanges() {
        String id = createdId(world.alice(), world.orgA(), "Ops");
        createdId(world.alice(), world.orgA(), "Taken");

        HttpResponse<String> renamed =
                update(world.alice(), world.orgA(), id, "Operations", "Runs things", 0);
        assertThat(renamed.statusCode()).isEqualTo(200);
        assertThat(renamed.body())
                .contains("\"name\":\"Operations\"")
                .contains("\"description\":\"Runs things\"")
                .contains("\"version\":1");

        // Someone still holding version 0 must not overwrite the change
        HttpResponse<String> stale = update(world.ann(), world.orgA(), id, "Other", null, 0);
        assertThat(stale.statusCode()).isEqualTo(409);
        assertThat(stale.body()).contains("STALE_VERSION");

        // Renaming onto an existing name is refused
        HttpResponse<String> clash =
                update(world.alice(), world.orgA(), id, "taken", "Runs things", 1);
        assertThat(clash.statusCode()).isEqualTo(409);
        assertThat(clash.body()).contains("DEPARTMENT_NAME_TAKEN");

        // An update that changes nothing writes nothing: no new version, no new audit row
        HttpResponse<String> unchanged =
                update(world.alice(), world.orgA(), id, "Operations", "Runs things", 1);
        assertThat(unchanged.statusCode()).isEqualTo(200);
        assertThat(versionOf(unchanged.body())).isEqualTo(1);
        assertThat(auditCount("DEPARTMENT_UPDATED", id)).isEqualTo(1);

        String rename =
                jdbc.sql(
                                "SELECT (metadata ->> 'fromName') || '>' || (metadata ->> 'toName')"
                                        + " FROM audit_logs WHERE event_type = 'DEPARTMENT_UPDATED' AND target_id = ?")
                        .param(UUID.fromString(id))
                        .query(String.class)
                        .single();
        assertThat(rename).isEqualTo("Ops>Operations");
    }

    @Test
    void deactivatingKeepsTheDepartmentVisibleFilterableAndReversible() {
        String id = createdId(world.alice(), world.orgA(), "Archive Me");
        createdId(world.alice(), world.orgA(), "Keep Me");

        HttpResponse<String> deactivated = action(world.alice(), world.orgA(), id, "deactivate");
        assertThat(deactivated.statusCode()).isEqualTo(200);
        assertThat(deactivated.body()).contains("\"active\":false");
        long versionAfter = versionOf(deactivated.body());

        // Idempotent: no second audit row, no version bump
        HttpResponse<String> again = action(world.alice(), world.orgA(), id, "deactivate");
        assertThat(again.statusCode()).isEqualTo(200);
        assertThat(versionOf(again.body())).isEqualTo(versionAfter);
        assertThat(auditCount("DEPARTMENT_DEACTIVATED", id)).isEqualTo(1);

        assertThat(names(list(world.carol(), "?active=true").body())).containsExactly("Keep Me");
        assertThat(names(list(world.carol(), "?active=false").body()))
                .containsExactly("Archive Me");
        assertThat(names(list(world.carol(), "").body())).containsExactly("Archive Me", "Keep Me");
        assertThat(world.carol().http().get(base(world.orgA()) + "/" + id).statusCode())
                .isEqualTo(200);

        assertThat(action(world.alice(), world.orgA(), id, "reactivate").body())
                .contains("\"active\":true");
        assertThat(auditCount("DEPARTMENT_REACTIVATED", id)).isEqualTo(1);
    }

    @Test
    void theListIsPaginatedSortedAndFilterable() {
        for (String name : List.of("Echo", "Alpha", "Delta", "Bravo", "Charlie")) {
            createdId(world.alice(), world.orgA(), name);
        }
        action(
                world.alice(),
                world.orgA(),
                createdId(world.alice(), world.orgA(), "Zulu"),
                "deactivate");

        HttpResponse<String> all = list(world.carol(), "?size=100");
        assertThat(all.body()).contains("\"totalElements\":6");
        assertThat(names(all.body()))
                .containsExactly("Alpha", "Bravo", "Charlie", "Delta", "Echo", "Zulu");

        HttpResponse<String> secondPage = list(world.carol(), "?page=1&size=2");
        assertThat(names(secondPage.body())).containsExactly("Charlie", "Delta");
        assertThat(secondPage.body()).contains("\"totalPages\":3");

        assertThat(names(list(world.carol(), "?sort=NAME&direction=DESC&size=2").body()))
                .containsExactly("Zulu", "Echo");
        assertThat(names(list(world.carol(), "?q=ALP").body())).containsExactly("Alpha");
        assertThat(list(world.carol(), "?sort=CREATED&direction=DESC").statusCode()).isEqualTo(200);
        assertThat(list(world.carol(), "?sort=password").statusCode())
                .as("sort columns come from a whitelist")
                .isEqualTo(400);
        assertThat(list(world.carol(), "?size=100000").body()).contains("\"size\":100");
    }

    @Test
    void searchWildcardsMatchThemselvesAndNothingElse() {
        createdId(world.alice(), world.orgA(), "100% Club");
        createdId(world.alice(), world.orgA(), "Plain");
        createdId(world.alice(), world.orgA(), "A_B");
        createdId(world.alice(), world.orgA(), "AXB");

        assertThat(names(list(world.carol(), "?q=%25").body())).containsExactly("100% Club");
        assertThat(names(list(world.carol(), "?q=A_B").body())).containsExactly("A_B");
    }

    @Test
    void twoAdministratorsUpdatingTheSameDepartmentAtOnceProduceExactlyOneWinner()
            throws Exception {
        String id = createdId(world.alice(), world.orgA(), "Contested");
        String aliceCsrf = world.alice().http().csrfToken();
        String annCsrf = world.ann().http().csrfToken();

        List<Integer> statuses =
                Parallel.statuses(
                        List.of(
                                () ->
                                        update(
                                                world.alice(),
                                                world.orgA(),
                                                id,
                                                "Name One",
                                                null,
                                                0,
                                                aliceCsrf),
                                () ->
                                        update(
                                                world.ann(),
                                                world.orgA(),
                                                id,
                                                "Name Two",
                                                null,
                                                0,
                                                annCsrf)));

        assertThat(statuses).containsExactly(200, 409);
    }

    @Test
    void departmentIdsFromAnotherOrganizationAreNotFoundThroughAnyPath() {
        String bobsDepartment = createdId(world.bob(), world.orgB(), "Bobs Team");
        String alicesDepartment = createdId(world.alice(), world.orgA(), "Alices Team");

        // Alice cannot reach Bob's department through her own organization...
        assertThat(world.alice().http().get(base(world.orgA()) + "/" + bobsDepartment).statusCode())
                .isEqualTo(404);
        assertThat(
                        update(world.alice(), world.orgA(), bobsDepartment, "Hijacked", null, 0)
                                .statusCode())
                .isEqualTo(404);
        assertThat(action(world.alice(), world.orgA(), bobsDepartment, "deactivate").statusCode())
                .isEqualTo(404);
        // ...nor through his organization, where she is not a member
        assertThat(world.alice().http().get(base(world.orgB()) + "/" + bobsDepartment).statusCode())
                .isEqualTo(404);
        // And the reverse
        assertThat(world.bob().http().get(base(world.orgB()) + "/" + alicesDepartment).statusCode())
                .isEqualTo(404);
        assertThat(
                        update(world.bob(), world.orgB(), alicesDepartment, "Hijacked", null, 0)
                                .statusCode())
                .isEqualTo(404);

        HttpResponse<String> untouched =
                world.bob().http().get(base(world.orgB()) + "/" + bobsDepartment);
        assertThat(untouched.body())
                .contains("\"name\":\"Bobs Team\"")
                .contains("\"active\":true")
                .contains("\"version\":0");
    }
}
