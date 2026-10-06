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
class DepartmentMemberEndpointsIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired private TenantWorldFactory factory;
    @Autowired private JdbcClient jdbc;

    private TenantWorld world;
    private String department; // a department of Org A, created by alice

    @BeforeEach
    void setUp() {
        world = factory.create(port);
        department = createDepartment(world.alice(), world.orgA(), "Engineering");
    }

    private static String departments(UUID org) {
        return "/api/orgs/" + org + "/departments";
    }

    private String createDepartment(Member as, UUID org, String name) {
        HttpResponse<String> response =
                as.http()
                        .postJson(
                                departments(org),
                                "{\"name\":\"" + name + "\"}",
                                as.http().csrfToken());
        assertThat(response.statusCode()).as("creating " + name).isEqualTo(201);
        return idOf(response.body());
    }

    private UUID membershipOf(UUID org, Member member) {
        return jdbc.sql("SELECT id FROM memberships WHERE organization_id = ? AND user_id = ?")
                .params(org, member.userId())
                .query(UUID.class)
                .single();
    }

    private HttpResponse<String> assign(
            Member as, UUID org, String departmentId, UUID membershipId) {
        return as.http()
                .request(
                        "PUT",
                        departments(org) + "/" + departmentId + "/members/" + membershipId,
                        null,
                        as.http().csrfToken());
    }

    private HttpResponse<String> unassign(
            Member as, UUID org, String departmentId, UUID membershipId) {
        return as.http()
                .request(
                        "DELETE",
                        departments(org) + "/" + departmentId + "/members/" + membershipId,
                        null,
                        as.http().csrfToken());
    }

    private HttpResponse<String> listMembers(Member as, String query) {
        return as.http().get(departments(world.orgA()) + "/" + department + "/members" + query);
    }

    private HttpResponse<String> deactivate(String departmentId) {
        return world.alice()
                .http()
                .request(
                        "POST",
                        departments(world.orgA()) + "/" + departmentId + "/deactivate",
                        null,
                        world.alice().http().csrfToken());
    }

    private static String idOf(String body) {
        Matcher matcher = Pattern.compile("\"id\":\"([0-9a-f-]{36})\"").matcher(body);
        assertThat(matcher.find()).as("an id in " + body).isTrue();
        return matcher.group(1);
    }

    private static List<String> displayNames(String body) {
        Matcher matcher = Pattern.compile("\"displayName\":\"([^\"]*)\"").matcher(body);
        List<String> names = new ArrayList<>();
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private int assignmentCount(UUID membershipId) {
        return jdbc.sql("SELECT count(*) FROM department_memberships WHERE membership_id = ?")
                .param(membershipId)
                .query(Integer.class)
                .single();
    }

    private int auditCount(String eventType, UUID membershipId) {
        return jdbc.sql(
                        "SELECT count(*) FROM audit_logs WHERE event_type = ? AND"
                                + " metadata ->> 'membershipId' = ?")
                .params(eventType, membershipId.toString())
                .query(Integer.class)
                .single();
    }

    @Test
    void theDepartmentMemberListIsInvisibleToEveryoneOutsideTheOrganization() {
        CrossTenantAssertions.assertIsolated(
                world,
                (client, segment) ->
                        client.get(
                                "/api/orgs/"
                                        + segment
                                        + "/departments/"
                                        + department
                                        + "/members"));
    }

    @Test
    void assigningIsInvisibleToEveryoneOutsideTheOrganization() {
        UUID carol = membershipOf(world.orgA(), world.carol());
        CrossTenantAssertions.assertIsolated(
                world,
                (client, segment) ->
                        client.request(
                                "PUT",
                                "/api/orgs/"
                                        + segment
                                        + "/departments/"
                                        + department
                                        + "/members/"
                                        + carol,
                                null,
                                client.csrfToken()));
    }

    @Test
    void unassigningIsInvisibleToEveryoneOutsideTheOrganization() {
        UUID carol = membershipOf(world.orgA(), world.carol());
        CrossTenantAssertions.assertIsolated(
                world,
                (client, segment) ->
                        client.request(
                                "DELETE",
                                "/api/orgs/"
                                        + segment
                                        + "/departments/"
                                        + department
                                        + "/members/"
                                        + carol,
                                null,
                                client.csrfToken()));
    }

    @Test
    void assignedMembersAreListedByNameAndEmailsAreOnlyShownToAdministrators() {
        assertThat(
                        assign(
                                        world.alice(),
                                        world.orgA(),
                                        department,
                                        membershipOf(world.orgA(), world.dan()))
                                .statusCode())
                .isEqualTo(204);
        assertThat(
                        assign(
                                        world.alice(),
                                        world.orgA(),
                                        department,
                                        membershipOf(world.orgA(), world.carol()))
                                .statusCode())
                .isEqualTo(204);

        HttpResponse<String> asOwner = listMembers(world.alice(), "");
        HttpResponse<String> asEmployee = listMembers(world.carol(), "");

        assertThat(asOwner.statusCode()).isEqualTo(200);
        assertThat(displayNames(asOwner.body())).containsExactly("carol", "dan");
        assertThat(asOwner.body()).contains("\"totalElements\":2").contains(world.carol().email());
        assertThat(asEmployee.statusCode()).isEqualTo(200);
        assertThat(displayNames(asEmployee.body())).containsExactly("carol", "dan");
        assertThat(asEmployee.body()).doesNotContain("@example.com");
    }

    @Test
    void assigningTwiceIsIdempotentAndAuditedOnce() {
        UUID carol = membershipOf(world.orgA(), world.carol());

        assertThat(assign(world.alice(), world.orgA(), department, carol).statusCode())
                .isEqualTo(204);
        assertThat(assign(world.ann(), world.orgA(), department, carol).statusCode())
                .isEqualTo(204);

        assertThat(assignmentCount(carol)).isEqualTo(1);
        assertThat(auditCount("DEPARTMENT_MEMBER_ADDED", carol)).isEqualTo(1);
        assertThat(unassign(world.alice(), world.orgA(), department, carol).statusCode())
                .isEqualTo(204);
        assertThat(unassign(world.alice(), world.orgA(), department, carol).statusCode())
                .as("removing again is a no-op")
                .isEqualTo(204);
        assertThat(assignmentCount(carol)).isZero();
        assertThat(auditCount("DEPARTMENT_MEMBER_REMOVED", carol)).isEqualTo(1);
    }

    @Test
    void onlyOwnersAndAdminsCanChangeAssignmentsButEveryoneCanView() {
        UUID frank = membershipOf(world.orgA(), world.frank());
        assign(world.alice(), world.orgA(), department, frank);

        for (Member refused : List.of(world.dan(), world.carol())) {
            assertThat(
                            assign(
                                            refused,
                                            world.orgA(),
                                            department,
                                            membershipOf(world.orgA(), refused))
                                    .statusCode())
                    .isEqualTo(403);
            assertThat(unassign(refused, world.orgA(), department, frank).statusCode())
                    .isEqualTo(403);
            assertThat(listMembers(refused, "").statusCode()).isEqualTo(200);
        }
        assertThat(assignmentCount(frank)).isEqualTo(1);
    }

    @Test
    void idsFromAnotherOrganizationAreNotFound() {
        String bobsDepartment = createDepartment(world.bob(), world.orgB(), "Bobs Team");
        UUID bobsMembership = membershipOf(world.orgB(), world.bob());
        UUID carol = membershipOf(world.orgA(), world.carol());

        // Another organization's member in my department, and my member in another's department
        assertThat(assign(world.alice(), world.orgA(), department, bobsMembership).statusCode())
                .isEqualTo(404);
        assertThat(assign(world.alice(), world.orgA(), bobsDepartment, carol).statusCode())
                .isEqualTo(404);
        assertThat(unassign(world.alice(), world.orgA(), bobsDepartment, carol).statusCode())
                .isEqualTo(404);
        assertThat(listMembers(world.alice(), "").statusCode()).isEqualTo(200);
        assertThat(
                        world.alice()
                                .http()
                                .get(departments(world.orgA()) + "/" + bobsDepartment + "/members")
                                .statusCode())
                .isEqualTo(404);
        assertThat(assignmentCount(bobsMembership)).isZero();
        assertThat(assignmentCount(carol)).isZero();
    }

    @Test
    void aDeactivatedDepartmentRefusesNewMembersButCanStillBeEmptied() {
        UUID carol = membershipOf(world.orgA(), world.carol());
        UUID dan = membershipOf(world.orgA(), world.dan());
        assign(world.alice(), world.orgA(), department, carol);
        deactivate(department);

        HttpResponse<String> refused = assign(world.alice(), world.orgA(), department, dan);

        assertThat(refused.statusCode()).isEqualTo(409);
        assertThat(refused.body()).contains("DEPARTMENT_INACTIVE");
        assertThat(displayNames(listMembers(world.alice(), "").body())).containsExactly("carol");
        assertThat(unassign(world.alice(), world.orgA(), department, carol).statusCode())
                .isEqualTo(204);
        assertThat(assignmentCount(carol)).isZero();
    }

    @Test
    void removingOrLeavingClearsAMembersAssignments() {
        UUID frank = membershipOf(world.orgA(), world.frank());
        UUID carol = membershipOf(world.orgA(), world.carol());
        String second = createDepartment(world.alice(), world.orgA(), "Finance");
        assign(world.alice(), world.orgA(), department, frank);
        assign(world.alice(), world.orgA(), second, frank);
        assign(world.alice(), world.orgA(), department, carol);

        // An administrator removes frank
        HttpResponse<String> removed =
                world.ann()
                        .http()
                        .request(
                                "DELETE",
                                "/api/orgs/" + world.orgA() + "/members/" + frank,
                                null,
                                world.ann().http().csrfToken());
        // carol leaves on her own
        HttpResponse<String> left =
                world.carol()
                        .http()
                        .request(
                                "POST",
                                "/api/orgs/" + world.orgA() + "/leave",
                                null,
                                world.carol().http().csrfToken());

        assertThat(removed.statusCode()).isEqualTo(204);
        assertThat(left.statusCode()).isEqualTo(204);
        assertThat(assignmentCount(frank)).isZero();
        assertThat(assignmentCount(carol)).isZero();
    }

    @Test
    void revokedMembersAreNeverListedAndCannotBeAssigned() {
        UUID dan = membershipOf(world.orgA(), world.dan());
        assign(world.alice(), world.orgA(), department, dan);
        jdbc.sql("UPDATE memberships SET status = 'REVOKED' WHERE id = ?").param(dan).update();

        assertThat(displayNames(listMembers(world.alice(), "").body())).doesNotContain("dan");
        assertThat(assign(world.alice(), world.orgA(), department, dan).statusCode())
                .isEqualTo(404);
        assertThat(
                        world.alice()
                                .http()
                                .get(
                                        "/api/orgs/"
                                                + world.orgA()
                                                + "/members/"
                                                + dan
                                                + "/departments")
                                .statusCode())
                .isEqualTo(404);
    }

    @Test
    void aMembersDepartmentsAreListedIncludingInactiveOnes() {
        UUID carol = membershipOf(world.orgA(), world.carol());
        String finance = createDepartment(world.alice(), world.orgA(), "Finance");
        assign(world.alice(), world.orgA(), department, carol);
        assign(world.alice(), world.orgA(), finance, carol);
        deactivate(finance);

        HttpResponse<String> response =
                world.dan()
                        .http()
                        .get("/api/orgs/" + world.orgA() + "/members/" + carol + "/departments");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("\"name\":\"Engineering\"", "\"name\":\"Finance\"")
                .contains("\"active\":false");
    }

    @Test
    void assigningWhileRemovingNeverLeavesADanglingAssignment() throws Exception {
        UUID carol = membershipOf(world.orgA(), world.carol());
        String aliceCsrf = world.alice().http().csrfToken();
        String annCsrf = world.ann().http().csrfToken();

        List<Integer> statuses =
                Parallel.statuses(
                        List.of(
                                () ->
                                        world.alice()
                                                .http()
                                                .request(
                                                        "PUT",
                                                        departments(world.orgA())
                                                                + "/"
                                                                + department
                                                                + "/members/"
                                                                + carol,
                                                        null,
                                                        aliceCsrf),
                                () ->
                                        world.ann()
                                                .http()
                                                .request(
                                                        "DELETE",
                                                        "/api/orgs/"
                                                                + world.orgA()
                                                                + "/members/"
                                                                + carol,
                                                        null,
                                                        annCsrf)));

        // Either order is valid, but the outcome never is "revoked member still assigned"
        assertThat(statuses.get(0)).isEqualTo(204);
        assertThat(statuses.get(1)).isIn(204, 404);
        assertThat(assignmentCount(carol)).isZero();
        assertThat(
                        jdbc.sql("SELECT status FROM memberships WHERE id = ?")
                                .param(carol)
                                .query(String.class)
                                .single())
                .isEqualTo("REVOKED");
    }

    @Test
    void theMemberListIsPaginated() {
        for (Member member : List.of(world.ann(), world.carol(), world.dan(), world.frank())) {
            assign(world.alice(), world.orgA(), department, membershipOf(world.orgA(), member));
        }

        HttpResponse<String> first = listMembers(world.alice(), "?page=0&size=3");
        HttpResponse<String> second = listMembers(world.alice(), "?page=1&size=3");

        assertThat(displayNames(first.body())).containsExactly("ann", "carol", "dan");
        assertThat(first.body()).contains("\"totalElements\":4").contains("\"totalPages\":2");
        assertThat(displayNames(second.body())).containsExactly("frank");
    }
}
