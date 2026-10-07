package com.l2c.nexus.request.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.l2c.nexus.TestcontainersConfiguration;
import com.l2c.nexus.support.CrossTenantAssertions;
import com.l2c.nexus.support.Parallel;
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
class ServiceRequestEndpointsIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired private TenantWorldFactory factory;
    @Autowired private JdbcClient jdbc;

    private TenantWorld world;

    @BeforeEach
    void setUp() {
        world = factory.create(port);
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    private static String requests(UUID org) {
        return "/api/orgs/" + org + "/requests";
    }

    private static String json(String title, String category, String... extras) {
        StringBuilder body =
                new StringBuilder("{\"title\":\"")
                        .append(title)
                        .append("\",\"category\":\"")
                        .append(category)
                        .append("\"");
        for (String extra : extras) {
            body.append(",").append(extra);
        }
        return body.append("}").toString();
    }

    private HttpResponse<String> createIn(Member as, UUID org, String json) {
        return as.http().postJson(requests(org), json, as.http().csrfToken());
    }

    private HttpResponse<String> create(Member as, String json) {
        return createIn(as, world.orgA(), json);
    }

    private String createdId(Member as, String title, String... extras) {
        HttpResponse<String> response = create(as, json(title, "IT_SUPPORT", extras));
        assertThat(response.statusCode()).as("creating " + title).isEqualTo(201);
        return field(response.body(), "id");
    }

    private HttpResponse<String> get(Member as, UUID org, String id) {
        return as.http().get(requests(org) + "/" + id);
    }

    private HttpResponse<String> update(Member as, UUID org, String id, String json) {
        return as.http().request("PUT", requests(org) + "/" + id, json, as.http().csrfToken());
    }

    private static String updateJson(
            String title, String category, long version, String... extras) {
        String base = json(title, category, extras);
        return base.substring(0, base.length() - 1) + ",\"version\":" + version + "}";
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

    private String createDepartment(Member as, UUID org, String name) {
        HttpResponse<String> response =
                as.http()
                        .postJson(
                                "/api/orgs/" + org + "/departments",
                                "{\"name\":\"" + name + "\"}",
                                as.http().csrfToken());
        assertThat(response.statusCode()).isEqualTo(201);
        return field(response.body(), "id");
    }

    private void deactivateDepartment(String id) {
        world.alice()
                .http()
                .request(
                        "POST",
                        "/api/orgs/" + world.orgA() + "/departments/" + id + "/deactivate",
                        null,
                        world.alice().http().csrfToken());
    }

    private static String field(String body, String name) {
        Matcher matcher = Pattern.compile("\"" + name + "\":\"([^\"]*)\"").matcher(body);
        assertThat(matcher.find()).as(name + " in " + body).isTrue();
        return matcher.group(1);
    }

    private static long versionOf(String body) {
        Matcher matcher = Pattern.compile("\"version\":(\\d+)").matcher(body);
        assertThat(matcher.find()).as("a version in " + body).isTrue();
        return Long.parseLong(matcher.group(1));
    }

    private static List<String> all(String body, String name) {
        Matcher matcher = Pattern.compile("\"" + name + "\":\"([^\"]*)\"").matcher(body);
        List<String> values = new ArrayList<>();
        while (matcher.find()) {
            values.add(matcher.group(1));
        }
        return values;
    }

    private static List<String> titles(String body) {
        return all(body, "title");
    }

    private static List<String> references(String body) {
        return all(body, "reference");
    }

    @Test
    void theRequestListIsInvisibleToEveryoneOutsideTheOrganization() {
        CrossTenantAssertions.assertIsolated(
                world, (client, segment) -> client.get("/api/orgs/" + segment + "/requests"));
    }

    @Test
    void creatingARequestIsInvisibleToEveryoneOutsideTheOrganization() {
        CrossTenantAssertions.assertIsolated(
                world,
                (client, segment) ->
                        client.postJson(
                                "/api/orgs/" + segment + "/requests",
                                json("Battery", "HR"),
                                client.csrfToken()));
    }

    @Test
    void aRequestDetailIsInvisibleToEveryoneOutsideTheOrganization() {
        String id = createdId(world.carol(), "Private matter");

        CrossTenantAssertions.assertIsolated(
                world, (client, segment) -> client.get("/api/orgs/" + segment + "/requests/" + id));
    }

    @Test
    void editingARequestIsInvisibleToEveryoneOutsideTheOrganization() {
        String id = createdId(world.carol(), "Private matter");

        CrossTenantAssertions.assertIsolated(
                world,
                (client, segment) ->
                        client.request(
                                "PUT",
                                "/api/orgs/" + segment + "/requests/" + id,
                                updateJson("Hijack", "HR", 0),
                                client.csrfToken()));
    }

    @Test
    void anEmployeeCreatesADraftWithANormalizedTitleAndTheirOwnMembership() {
        HttpResponse<String> response =
                create(
                        world.carol(),
                        json(
                                "  Replace   my laptop ",
                                "IT_SUPPORT",
                                "\"description\":\"  Screen is cracked  \"",
                                "\"dueDate\":\"" + today().plusDays(7) + "\""));

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.body())
                .contains("\"reference\":\"REQ-000001\"")
                .contains("\"title\":\"Replace my laptop\"")
                .contains("\"description\":\"Screen is cracked\"")
                .contains("\"category\":\"IT_SUPPORT\"")
                .contains("\"status\":\"DRAFT\"")
                .contains("\"createdByName\":\"carol\"")
                .contains("\"dueDate\":\"" + today().plusDays(7) + "\"")
                .contains("\"version\":0")
                .contains("\"editable\":true");
        assertThat(field(response.body(), "createdByMembershipId"))
                .isEqualTo(membershipOf(world.carol()).toString());
    }

    @Test
    void referencesAreSequentialPerOrganizationAndIndependentAcrossOrganizations() {
        HttpResponse<String> first = create(world.alice(), json("One", "HR"));
        HttpResponse<String> second = create(world.carol(), json("Two", "HR"));
        HttpResponse<String> otherOrganization =
                createIn(world.bob(), world.orgB(), json("Three", "HR"));

        assertThat(field(first.body(), "reference")).isEqualTo("REQ-000001");
        assertThat(field(second.body(), "reference")).isEqualTo("REQ-000002");
        assertThat(field(otherOrganization.body(), "reference")).isEqualTo("REQ-000001");
    }

    @Test
    void simultaneousCreationsNeverShareAReference() throws Exception {
        List<Member> creators = List.of(world.alice(), world.ann(), world.dan(), world.carol());
        List<String> csrf = creators.stream().map(member -> member.http().csrfToken()).toList();

        List<Integer> statuses =
                Parallel.statuses(
                        List.of(
                                () ->
                                        createIn(
                                                creators.get(0),
                                                world.orgA(),
                                                json("Parallel 0", "HR")),
                                () ->
                                        createIn(
                                                creators.get(1),
                                                world.orgA(),
                                                json("Parallel 1", "HR")),
                                () ->
                                        createIn(
                                                creators.get(2),
                                                world.orgA(),
                                                json("Parallel 2", "HR")),
                                () ->
                                        createIn(
                                                creators.get(3),
                                                world.orgA(),
                                                json("Parallel 3", "HR"))));

        assertThat(statuses).containsExactly(201, 201, 201, 201);
        assertThat(csrf).hasSize(4);
        int distinct =
                jdbc.sql(
                                "SELECT count(DISTINCT reference) FROM service_requests WHERE organization_id = ?")
                        .param(world.orgA())
                        .query(Integer.class)
                        .single();
        long counter =
                jdbc.sql("SELECT request_counter FROM organizations WHERE id = ?")
                        .param(world.orgA())
                        .query(Long.class)
                        .single();
        assertThat(distinct).isEqualTo(4);
        assertThat(counter).isEqualTo(4);
    }

    @Test
    void employeesSeeOnlyTheirOwnRequestsWhileReviewersSeeEverything() {
        String carolsId = createdId(world.carol(), "Carols request");
        String franksId = createdId(world.frank(), "Franks request");

        assertThat(titles(list(world.carol(), "").body())).containsExactly("Carols request");
        assertThat(titles(list(world.frank(), "").body())).containsExactly("Franks request");
        assertThat(titles(list(world.dan(), "").body()))
                .containsExactly("Franks request", "Carols request");
        assertThat(titles(list(world.alice(), "").body())).hasSize(2);

        assertThat(get(world.carol(), world.orgA(), franksId).statusCode())
                .as("an employee cannot see someone else's request")
                .isEqualTo(404);
        assertThat(get(world.dan(), world.orgA(), franksId).statusCode()).isEqualTo(200);
        assertThat(get(world.carol(), world.orgA(), carolsId).body()).contains("\"editable\":true");
        assertThat(get(world.dan(), world.orgA(), carolsId).body()).contains("\"editable\":false");
    }

    @Test
    void anEmployeeCannotWidenWhatTheySeeWithFilters() {
        createdId(world.frank(), "Franks request");
        createdId(world.carol(), "Carols request");

        HttpResponse<String> othersRequests =
                list(world.carol(), "?createdBy=" + membershipOf(world.frank()));
        HttpResponse<String> ownByFilter =
                list(world.carol(), "?createdBy=" + membershipOf(world.carol()));

        assertThat(othersRequests.body()).contains("\"totalElements\":0");
        assertThat(titles(ownByFilter.body())).containsExactly("Carols request");
    }

    @Test
    void theListFiltersByStatusCategoryDepartmentCreatorDatesAndSearch() {
        String engineering = createDepartment(world.alice(), world.orgA(), "Engineering");
        create(
                world.alice(),
                json("Laptop refresh", "IT_SUPPORT", "\"departmentId\":\"" + engineering + "\""));
        create(world.alice(), json("Office chairs", "FACILITIES"));
        create(world.dan(), json("Budget approval", "FINANCE"));

        assertThat(titles(list(world.alice(), "?category=FACILITIES").body()))
                .containsExactly("Office chairs");
        assertThat(titles(list(world.alice(), "?departmentId=" + engineering).body()))
                .containsExactly("Laptop refresh");
        assertThat(titles(list(world.alice(), "?createdBy=" + membershipOf(world.dan())).body()))
                .containsExactly("Budget approval");
        assertThat(titles(list(world.alice(), "?mine=true").body()))
                .containsExactly("Office chairs", "Laptop refresh");
        assertThat(titles(list(world.alice(), "?q=CHAIR").body())).containsExactly("Office chairs");
        assertThat(titles(list(world.alice(), "?q=REQ-000003").body()))
                .containsExactly("Budget approval");
        assertThat(titles(list(world.alice(), "?status=DRAFT").body())).hasSize(3);
        assertThat(list(world.alice(), "?status=SUBMITTED").body()).contains("\"totalElements\":0");
        assertThat(titles(list(world.alice(), "?createdFrom=" + today()).body())).hasSize(3);
        assertThat(list(world.alice(), "?createdFrom=" + today().plusDays(1)).body())
                .contains("\"totalElements\":0");
        assertThat(list(world.alice(), "?createdTo=" + today().minusDays(1)).body())
                .contains("\"totalElements\":0");
        assertThat(titles(list(world.alice(), "?createdTo=" + today()).body())).hasSize(3);
    }

    @Test
    void searchTextIsNeverTreatedAsSqlOrAsWildcards() {
        create(world.alice(), json("100% done", "HR"));
        create(world.alice(), json("Plain", "HR"));

        HttpResponse<String> injection = list(world.alice(), "?q=%27%20OR%201%3D1%20--");
        HttpResponse<String> percent = list(world.alice(), "?q=%25");

        assertThat(injection.statusCode()).isEqualTo(200);
        assertThat(injection.body()).contains("\"totalElements\":0");
        assertThat(titles(percent.body())).containsExactly("100% done");
    }

    @Test
    void theListIsPaginatedAndSortedWithEmptyDueDatesLast() {
        create(world.alice(), json("R1", "HR", "\"dueDate\":\"" + today().plusDays(5) + "\""));
        create(world.alice(), json("R2", "HR"));
        create(world.alice(), json("R3", "HR", "\"dueDate\":\"" + today().plusDays(1) + "\""));
        create(world.alice(), json("R4", "HR", "\"dueDate\":\"" + today().plusDays(3) + "\""));
        create(world.alice(), json("R5", "HR"));

        HttpResponse<String> first = list(world.alice(), "?page=0&size=2");
        HttpResponse<String> last = list(world.alice(), "?page=2&size=2");
        assertThat(titles(first.body())).containsExactly("R5", "R4");
        assertThat(first.body()).contains("\"totalElements\":5").contains("\"totalPages\":3");
        assertThat(titles(last.body())).containsExactly("R1");
        assertThat(titles(list(world.alice(), "?sort=CREATED&direction=ASC&size=1").body()))
                .containsExactly("R1");

        List<String> ascending =
                titles(list(world.alice(), "?sort=DUE_DATE&direction=ASC&size=100").body());
        assertThat(ascending.subList(0, 3)).containsExactly("R3", "R4", "R1");
        assertThat(ascending.subList(3, 5)).containsExactlyInAnyOrder("R2", "R5");
        List<String> descending =
                titles(list(world.alice(), "?sort=DUE_DATE&direction=DESC&size=100").body());
        assertThat(descending.subList(0, 3)).containsExactly("R1", "R4", "R3");
        assertThat(list(world.alice(), "?size=100000").body()).contains("\"size\":100");
    }

    @Test
    void invalidQueryParametersAreRejected() {
        assertThat(list(world.alice(), "?sort=password").statusCode()).isEqualTo(400);
        assertThat(list(world.alice(), "?status=NOPE").statusCode()).isEqualTo(400);
        assertThat(list(world.alice(), "?createdFrom=yesterday").statusCode()).isEqualTo(400);
        assertThat(list(world.alice(), "?createdFrom=2026-13-45").statusCode()).isEqualTo(400);
        HttpResponse<String> huge = list(world.alice(), "?createdFrom=%2B999999999-01-01");
        assertThat(huge.statusCode()).isEqualTo(400);
    }

    @Test
    void theCreatorEditsADraftWithTheVersionTheySawAndNoOpEditsWriteNothing() {
        String id = createdId(world.carol(), "Original");

        HttpResponse<String> edited =
                update(
                        world.carol(),
                        world.orgA(),
                        id,
                        updateJson("Renamed", "HR", 0, "\"description\":\"More detail\""));
        assertThat(edited.statusCode()).isEqualTo(200);
        assertThat(edited.body())
                .contains("\"title\":\"Renamed\"")
                .contains("\"category\":\"HR\"")
                .contains("\"description\":\"More detail\"")
                .contains("\"version\":1");

        HttpResponse<String> stale =
                update(world.carol(), world.orgA(), id, updateJson("Other", "HR", 0));
        assertThat(stale.statusCode()).isEqualTo(409);
        assertThat(stale.body()).contains("STALE_VERSION");

        HttpResponse<String> unchanged =
                update(
                        world.carol(),
                        world.orgA(),
                        id,
                        updateJson("Renamed", "HR", 1, "\"description\":\"More detail\""));
        assertThat(unchanged.statusCode()).isEqualTo(200);
        assertThat(versionOf(unchanged.body())).isEqualTo(1);
    }

    @Test
    void onlyTheCreatorCanEditEvenOwnersAndAdministrators() {
        String id = createdId(world.carol(), "Carols request");

        for (Member reviewer : List.of(world.dan(), world.ann(), world.alice())) {
            HttpResponse<String> response =
                    update(reviewer, world.orgA(), id, updateJson("Hijacked", "HR", 0));
            assertThat(response.statusCode()).as(reviewer.email()).isEqualTo(403);
            assertThat(response.body()).contains("NOT_REQUEST_OWNER");
        }
        assertThat(
                        update(world.frank(), world.orgA(), id, updateJson("Hijacked", "HR", 0))
                                .statusCode())
                .as("someone who cannot even see it")
                .isEqualTo(404);
        assertThat(get(world.carol(), world.orgA(), id).body())
                .contains("\"title\":\"Carols request\"");
    }

    @Test
    void requestsThatAreNoLongerDraftsCannotBeEditedUntilSentBack() {
        String id = createdId(world.carol(), "In review");

        jdbc.sql("UPDATE service_requests SET status = 'SUBMITTED' WHERE id = ?")
                .param(UUID.fromString(id))
                .update();
        HttpResponse<String> submitted =
                update(world.carol(), world.orgA(), id, updateJson("Edit", "HR", 0));
        assertThat(submitted.statusCode()).isEqualTo(409);
        assertThat(submitted.body()).contains("REQUEST_NOT_EDITABLE");
        assertThat(get(world.carol(), world.orgA(), id).body()).contains("\"editable\":false");

        jdbc.sql("UPDATE service_requests SET status = 'CHANGES_REQUESTED' WHERE id = ?")
                .param(UUID.fromString(id))
                .update();
        assertThat(
                        update(world.carol(), world.orgA(), id, updateJson("Edit", "HR", 0))
                                .statusCode())
                .isEqualTo(200);

        jdbc.sql("UPDATE service_requests SET status = 'APPROVED' WHERE id = ?")
                .param(UUID.fromString(id))
                .update();
        assertThat(
                        update(world.carol(), world.orgA(), id, updateJson("Again", "HR", 1))
                                .statusCode())
                .isEqualTo(409);
    }

    @Test
    void invalidInputIsRejectedWithFieldErrorsAndConsumesNoReferenceNumber() {
        HttpResponse<String> blank = create(world.carol(), json("", "HR"));
        HttpResponse<String> tooLong = create(world.carol(), json("x".repeat(151), "HR"));
        HttpResponse<String> control = create(world.carol(), json("bad\\u0007title", "HR"));
        HttpResponse<String> longDescription =
                create(
                        world.carol(),
                        json("Ok", "HR", "\"description\":\"" + "d".repeat(5001) + "\""));
        HttpResponse<String> noCategory = create(world.carol(), "{\"title\":\"Ok\"}");
        HttpResponse<String> unknownCategory = create(world.carol(), json("Ok", "GARDENING"));

        assertThat(blank.body()).contains("\"errors\"").contains("\"title\"");
        assertThat(tooLong.body()).contains("\"title\"");
        assertThat(control.body()).contains("\"title\"");
        assertThat(longDescription.body()).contains("\"description\"");
        assertThat(noCategory.body()).contains("\"category\"");
        for (HttpResponse<String> response :
                List.of(blank, tooLong, control, longDescription, noCategory, unknownCategory)) {
            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(response.headers().firstValue("Content-Type").orElse(""))
                    .contains("application/problem+json");
        }
        // Rejected requests leave no trace: the next valid one is still number 1
        assertThat(field(create(world.carol(), json("Fine", "HR")).body(), "reference"))
                .isEqualTo("REQ-000001");
    }

    @Test
    void dueDatesMustBeTodayOrLaterWithinTenYearsExceptWhenUnchanged() {
        HttpResponse<String> past =
                create(
                        world.carol(),
                        json("Late", "HR", "\"dueDate\":\"" + today().minusDays(1) + "\""));
        HttpResponse<String> far =
                create(
                        world.carol(),
                        json("Far", "HR", "\"dueDate\":\"" + today().plusYears(11) + "\""));
        HttpResponse<String> todayOk =
                create(world.carol(), json("Today", "HR", "\"dueDate\":\"" + today() + "\""));

        assertThat(past.statusCode()).isEqualTo(400);
        assertThat(past.body()).contains("\"dueDate\"");
        assertThat(far.statusCode()).isEqualTo(400);
        assertThat(todayOk.statusCode()).isEqualTo(201);

        String id = createdId(world.carol(), "Soon", "\"dueDate\":\"" + today().plusDays(2) + "\"");
        jdbc.sql("UPDATE service_requests SET due_date = ? WHERE id = ?")
                .params(today().minusDays(5), UUID.fromString(id))
                .update();
        String lateDate = today().minusDays(5).toString();

        HttpResponse<String> retitled =
                update(
                        world.carol(),
                        world.orgA(),
                        id,
                        updateJson("Retitled", "HR", 0, "\"dueDate\":\"" + lateDate + "\""));
        HttpResponse<String> movedToAnotherPastDate =
                update(
                        world.carol(),
                        world.orgA(),
                        id,
                        updateJson(
                                "Retitled",
                                "HR",
                                1,
                                "\"dueDate\":\"" + today().minusDays(4) + "\""));

        assertThat(retitled.statusCode()).as("an unchanged late date may stay").isEqualTo(200);
        assertThat(movedToAnotherPastDate.statusCode()).isEqualTo(400);
    }

    @Test
    void departmentsMustBelongToTheOrganizationAndBeActiveWhenAssigned() {
        String engineering = createDepartment(world.alice(), world.orgA(), "Engineering");
        String bobsDepartment = createDepartment(world.bob(), world.orgB(), "Bobs Team");

        HttpResponse<String> withDepartment =
                create(
                        world.carol(),
                        json(
                                "Needs a department",
                                "HR",
                                "\"departmentId\":\"" + engineering + "\""));
        assertThat(withDepartment.statusCode()).isEqualTo(201);
        assertThat(withDepartment.body()).contains("\"departmentName\":\"Engineering\"");
        assertThat(
                        create(
                                        world.carol(),
                                        json(
                                                "Wrong organization",
                                                "HR",
                                                "\"departmentId\":\"" + bobsDepartment + "\""))
                                .statusCode())
                .isEqualTo(404);
        assertThat(
                        create(
                                        world.carol(),
                                        json(
                                                "Unknown department",
                                                "HR",
                                                "\"departmentId\":\"" + UUID.randomUUID() + "\""))
                                .statusCode())
                .isEqualTo(404);

        deactivateDepartment(engineering);
        HttpResponse<String> inactive =
                create(
                        world.carol(),
                        json("Too late", "HR", "\"departmentId\":\"" + engineering + "\""));
        assertThat(inactive.statusCode()).isEqualTo(409);
        assertThat(inactive.body()).contains("DEPARTMENT_INACTIVE");

        // The existing request can keep its (now inactive) department while its title is fixed
        String id = field(withDepartment.body(), "id");
        HttpResponse<String> retitled =
                update(
                        world.carol(),
                        world.orgA(),
                        id,
                        updateJson(
                                "Retitled", "HR", 0, "\"departmentId\":\"" + engineering + "\""));
        assertThat(retitled.statusCode()).isEqualTo(200);
        String ops = createDepartment(world.alice(), world.orgA(), "Operations");
        deactivateDepartment(ops);
        assertThat(
                        update(
                                        world.carol(),
                                        world.orgA(),
                                        id,
                                        updateJson(
                                                "Retitled",
                                                "HR",
                                                1,
                                                "\"departmentId\":\"" + ops + "\""))
                                .statusCode())
                .isEqualTo(409);
        assertThat(get(world.carol(), world.orgA(), id).body())
                .contains("\"departmentName\":\"Engineering\"");
    }

    @Test
    void requestIdsFromAnotherOrganizationAreNotFoundThroughAnyPath() {
        HttpResponse<String> bobsRequest =
                createIn(world.bob(), world.orgB(), json("Bobs secret", "FINANCE"));
        String bobsId = field(bobsRequest.body(), "id");
        String alicesId = createdId(world.alice(), "Alices secret");

        assertThat(get(world.alice(), world.orgA(), bobsId).statusCode()).isEqualTo(404);
        assertThat(
                        update(world.alice(), world.orgA(), bobsId, updateJson("Hijacked", "HR", 0))
                                .statusCode())
                .isEqualTo(404);
        assertThat(get(world.alice(), world.orgB(), bobsId).statusCode()).isEqualTo(404);
        assertThat(get(world.bob(), world.orgB(), alicesId).statusCode()).isEqualTo(404);
        assertThat(
                        update(world.bob(), world.orgB(), alicesId, updateJson("Hijacked", "HR", 0))
                                .statusCode())
                .isEqualTo(404);
        assertThat(get(world.bob(), world.orgB(), bobsId).body())
                .contains("\"title\":\"Bobs secret\"")
                .contains("\"version\":0");
    }

    @Test
    void aRemovedMembersRequestsKeepTheirHistoryAndName() {
        String id = createdId(world.frank(), "Franks last request");

        HttpResponse<String> removed =
                world.ann()
                        .http()
                        .request(
                                "DELETE",
                                "/api/orgs/"
                                        + world.orgA()
                                        + "/members/"
                                        + membershipOf(world.frank()),
                                null,
                                world.ann().http().csrfToken());

        assertThat(removed.statusCode()).isEqualTo(204);
        assertThat(world.frank().http().get(requests(world.orgA())).statusCode()).isEqualTo(404);
        HttpResponse<String> asReviewer = get(world.dan(), world.orgA(), id);
        assertThat(asReviewer.statusCode()).isEqualTo(200);
        assertThat(asReviewer.body()).contains("\"createdByName\":\"frank\"");
        assertThat(list(world.dan(), "").body()).contains("\"createdByName\":\"frank\"");
    }
}
