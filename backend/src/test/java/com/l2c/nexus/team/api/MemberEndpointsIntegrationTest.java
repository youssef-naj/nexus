package com.l2c.nexus.team.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.l2c.nexus.TestcontainersConfiguration;
import com.l2c.nexus.support.CrossTenantAssertions;
import com.l2c.nexus.support.TenantWorld;
import com.l2c.nexus.support.TenantWorld.Member;
import com.l2c.nexus.support.TenantWorldFactory;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
class MemberEndpointsIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired private TenantWorldFactory factory;
    @Autowired private JdbcClient jdbc;

    private TenantWorld world;

    @BeforeEach
    void setUp() {
        world = factory.create(port);
    }

    private record Listed(String id, long version) {}

    private String members(UUID org) {
        return "/api/orgs/" + org + "/members";
    }

    private HttpResponse<String> list(Member as, String query) {
        return as.http().get(members(world.orgA()) + query);
    }

    private static List<String> names(String body) {
        Matcher matcher = Pattern.compile("\"displayName\":\"([^\"]*)\"").matcher(body);
        List<String> names = new ArrayList<>();
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    /** Finds a member of Org A by display name, as seen by the given member. */
    private Listed find(Member as, String name) {
        String body = list(as, "?size=100").body();
        Matcher object =
                Pattern.compile("\\{[^{}]*\"displayName\":\"" + name + "\"[^{}]*}").matcher(body);
        assertThat(object.find()).as(name + " in " + body).isTrue();
        Matcher id = Pattern.compile("\"id\":\"([0-9a-f-]{36})\"").matcher(object.group());
        Matcher version = Pattern.compile("\"version\":(\\d+)").matcher(object.group());
        assertThat(id.find() && version.find()).isTrue();
        return new Listed(id.group(1), Long.parseLong(version.group(1)));
    }

    private HttpResponse<String> changeRole(
            Member as, String membershipId, String role, long version, String csrf) {
        return as.http()
                .request(
                        "PATCH",
                        members(world.orgA()) + "/" + membershipId,
                        "{\"role\":\"" + role + "\",\"version\":" + version + "}",
                        csrf);
    }

    private HttpResponse<String> changeRole(
            Member as, String membershipId, String role, long version) {
        return changeRole(as, membershipId, role, version, as.http().csrfToken());
    }

    private HttpResponse<String> remove(Member as, UUID org, String membershipId) {
        return as.http()
                .request("DELETE", members(org) + "/" + membershipId, null, as.http().csrfToken());
    }

    private HttpResponse<String> leave(Member as) {
        return as.http()
                .request(
                        "POST",
                        "/api/orgs/" + world.orgA() + "/leave",
                        null,
                        as.http().csrfToken());
    }

    private int orgAStatusFor(Member as) {
        return as.http().get("/api/orgs/" + world.orgA()).statusCode();
    }

    private int activeOwners() {
        return jdbc.sql(
                        "SELECT count(*) FROM memberships WHERE organization_id = ? AND role ="
                                + " 'OWNER' AND status = 'ACTIVE'")
                .param(world.orgA())
                .query(Integer.class)
                .single();
    }

    private List<HttpResponse<String>> inParallel(List<Callable<HttpResponse<String>>> calls)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(calls.size());
        try {
            CountDownLatch ready = new CountDownLatch(calls.size());
            CountDownLatch go = new CountDownLatch(1);
            List<Future<HttpResponse<String>>> futures = new ArrayList<>();
            for (Callable<HttpResponse<String>> call : calls) {
                futures.add(
                        pool.submit(
                                () -> {
                                    ready.countDown();
                                    go.await();
                                    return call.call();
                                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<HttpResponse<String>> results = new ArrayList<>();
            for (Future<HttpResponse<String>> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private static List<Integer> sortedStatuses(List<HttpResponse<String>> responses) {
        return responses.stream().map(HttpResponse::statusCode).sorted().toList();
    }

    @Test
    void theMembersEndpointIsInvisibleToEveryoneOutsideTheOrganization() {
        CrossTenantAssertions.assertIsolated(
                world, (client, segment) -> client.get("/api/orgs/" + segment + "/members"));
    }

    @Test
    void everyMemberSeesTheTeamButOnlyAdministratorsSeeEmailAddresses() {
        HttpResponse<String> asEmployee = list(world.carol(), "");
        HttpResponse<String> asOwner = list(world.alice(), "");

        assertThat(asEmployee.statusCode()).isEqualTo(200);
        assertThat(names(asEmployee.body())).contains("alice", "ann", "dan", "carol", "frank");
        assertThat(asEmployee.body()).doesNotContain("@example.com");
        assertThat(asOwner.body()).contains(world.carol().email());
    }

    @Test
    void theListIsPaginatedSortedByNameAndFilterableByRole() {
        HttpResponse<String> first = list(world.alice(), "?page=0&size=2");
        HttpResponse<String> last = list(world.alice(), "?page=2&size=2");
        HttpResponse<String> employees = list(world.alice(), "?role=EMPLOYEE&size=100");

        assertThat(names(first.body())).containsExactly("alice", "ann");
        assertThat(first.body()).contains("\"totalElements\":5").contains("\"totalPages\":3");
        assertThat(names(last.body())).containsExactly("frank");
        assertThat(names(employees.body())).containsExactly("carol", "frank");
        assertThat(employees.body()).contains("\"totalElements\":2");
    }

    @Test
    void anOwnerChangesARoleAndTheChangeIsAuditedAndTakesEffect() {
        Listed carol = find(world.alice(), "carol");

        HttpResponse<String> response =
                changeRole(world.alice(), carol.id(), "MANAGER", carol.version());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("\"role\":\"MANAGER\"")
                .contains("\"version\":" + (carol.version() + 1));
        assertThat(world.carol().http().get("/api/orgs/" + world.orgA()).body())
                .contains("\"role\":\"MANAGER\"")
                .contains("REQUEST_REVIEW");
        String audited =
                jdbc.sql(
                                "SELECT (metadata ->> 'fromRole') || '>' || (metadata ->> 'toRole')"
                                        + " FROM audit_logs WHERE event_type = 'MEMBER_ROLE_CHANGED'"
                                        + " AND organization_id = ? AND actor_user_id = ?")
                        .params(world.orgA(), world.alice().userId())
                        .query(String.class)
                        .single();
        assertThat(audited).isEqualTo("EMPLOYEE>MANAGER");
    }

    @Test
    void anAdminCannotEscalateBeyondTheirOwnRoleOrTouchSomeoneWhoOutranksThem() {
        Listed carol = find(world.ann(), "carol");
        Listed alice = find(world.ann(), "alice");

        assertThat(changeRole(world.ann(), carol.id(), "OWNER", carol.version()).statusCode())
                .isEqualTo(403);
        assertThat(changeRole(world.ann(), carol.id(), "ADMIN", carol.version()).statusCode())
                .isEqualTo(403);
        assertThat(changeRole(world.ann(), alice.id(), "EMPLOYEE", alice.version()).statusCode())
                .as("an admin must not demote an owner")
                .isEqualTo(403);
        assertThat(changeRole(world.ann(), carol.id(), "MANAGER", carol.version()).statusCode())
                .isEqualTo(200);
    }

    @Test
    void nobodyCanChangeTheirOwnRole() {
        Listed alice = find(world.alice(), "alice");
        Listed ann = find(world.ann(), "ann");

        HttpResponse<String> owner =
                changeRole(world.alice(), alice.id(), "EMPLOYEE", alice.version());
        HttpResponse<String> admin = changeRole(world.ann(), ann.id(), "EMPLOYEE", ann.version());

        assertThat(owner.statusCode()).isEqualTo(403);
        assertThat(owner.body()).contains("CANNOT_CHANGE_OWN_ROLE");
        assertThat(admin.statusCode()).isEqualTo(403);
        assertThat(admin.body()).contains("CANNOT_CHANGE_OWN_ROLE");
    }

    @Test
    void managersAndEmployeesCannotChangeRoles() {
        Listed frank = find(world.alice(), "frank");

        assertThat(changeRole(world.dan(), frank.id(), "MANAGER", frank.version()).statusCode())
                .isEqualTo(403);
        assertThat(changeRole(world.carol(), frank.id(), "MANAGER", frank.version()).statusCode())
                .isEqualTo(403);
    }

    @Test
    void aStaleVersionIsRejectedSoNobodySilentlyOverwritesAnotherAdminsChange() {
        Listed seenByBoth = find(world.alice(), "carol");

        HttpResponse<String> first =
                changeRole(world.alice(), seenByBoth.id(), "MANAGER", seenByBoth.version());
        HttpResponse<String> second =
                changeRole(world.ann(), seenByBoth.id(), "EMPLOYEE", seenByBoth.version());

        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(second.statusCode()).isEqualTo(409);
        assertThat(second.body()).contains("STALE_VERSION");
    }

    @Test
    void twoAdministratorsEditingTheSameMemberAtOnceProduceExactlyOneWinner() throws Exception {
        Listed carol = find(world.alice(), "carol");
        String aliceCsrf = world.alice().http().csrfToken();
        String annCsrf = world.ann().http().csrfToken();

        List<HttpResponse<String>> results =
                inParallel(
                        List.of(
                                () ->
                                        changeRole(
                                                world.alice(),
                                                carol.id(),
                                                "MANAGER",
                                                carol.version(),
                                                aliceCsrf),
                                () ->
                                        changeRole(
                                                world.ann(),
                                                carol.id(),
                                                "MANAGER",
                                                carol.version(),
                                                annCsrf)));

        assertThat(sortedStatuses(results)).containsExactly(200, 409);
    }

    @Test
    void twoOwnersDemotingEachOtherAtOnceLeaveExactlyOneOwner() throws Exception {
        Listed ann = find(world.alice(), "ann");
        assertThat(changeRole(world.alice(), ann.id(), "OWNER", ann.version()).statusCode())
                .isEqualTo(200);
        Listed aliceNow = find(world.alice(), "alice");
        Listed annNow = find(world.alice(), "ann");
        String aliceCsrf = world.alice().http().csrfToken();
        String annCsrf = world.ann().http().csrfToken();

        List<HttpResponse<String>> results =
                inParallel(
                        List.of(
                                () ->
                                        changeRole(
                                                world.alice(),
                                                annNow.id(),
                                                "EMPLOYEE",
                                                annNow.version(),
                                                aliceCsrf),
                                () ->
                                        changeRole(
                                                world.ann(),
                                                aliceNow.id(),
                                                "EMPLOYEE",
                                                aliceNow.version(),
                                                annCsrf)));

        // The loser was demoted before its turn, so it no longer has the right to demote anyone
        assertThat(sortedStatuses(results)).containsExactly(200, 403);
        assertThat(activeOwners()).isEqualTo(1);
    }

    @Test
    void removingAMemberEndsTheirAccessImmediatelyAndIsAudited() {
        Listed frank = find(world.alice(), "frank");
        assertThat(orgAStatusFor(world.frank())).isEqualTo(200);

        HttpResponse<String> response = remove(world.alice(), world.orgA(), frank.id());

        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(orgAStatusFor(world.frank())).isEqualTo(404);
        assertThat(names(list(world.alice(), "?size=100").body())).doesNotContain("frank");
        String role =
                jdbc.sql(
                                "SELECT metadata ->> 'role' FROM audit_logs WHERE event_type ="
                                        + " 'MEMBER_REMOVED' AND organization_id = ?")
                        .param(world.orgA())
                        .query(String.class)
                        .single();
        assertThat(role).isEqualTo("EMPLOYEE");
    }

    @Test
    void removalRespectsRolesAndNeverAllowsRemovingYourself() {
        Listed alice = find(world.alice(), "alice");
        Listed carol = find(world.alice(), "carol");

        HttpResponse<String> self = remove(world.alice(), world.orgA(), alice.id());
        assertThat(self.statusCode()).isEqualTo(403);
        assertThat(self.body()).contains("CANNOT_REMOVE_SELF");
        assertThat(remove(world.ann(), world.orgA(), alice.id()).statusCode())
                .as("an admin must not remove an owner")
                .isEqualTo(403);
        assertThat(remove(world.dan(), world.orgA(), carol.id()).statusCode())
                .as("a manager cannot remove members")
                .isEqualTo(403);
        assertThat(remove(world.ann(), world.orgA(), carol.id()).statusCode()).isEqualTo(204);
    }

    @Test
    void anyMemberCanLeaveButTheLastOwnerCannot() {
        assertThat(leave(world.carol()).statusCode()).isEqualTo(204);
        assertThat(orgAStatusFor(world.carol())).isEqualTo(404);

        HttpResponse<String> lastOwner = leave(world.alice());

        assertThat(lastOwner.statusCode()).isEqualTo(409);
        assertThat(lastOwner.body()).contains("LAST_OWNER");
        assertThat(orgAStatusFor(world.alice())).isEqualTo(200);
    }

    @Test
    void anOwnerCanLeaveOnceAnotherOwnerExists() {
        Listed ann = find(world.alice(), "ann");
        assertThat(changeRole(world.alice(), ann.id(), "OWNER", ann.version()).statusCode())
                .isEqualTo(200);

        assertThat(leave(world.alice()).statusCode()).isEqualTo(204);

        assertThat(orgAStatusFor(world.alice())).isEqualTo(404);
        assertThat(activeOwners()).isEqualTo(1);
        assertThat(leave(world.ann()).statusCode()).isEqualTo(409);
    }

    @Test
    void membershipIdsFromAnotherOrganizationAreNotFound() {
        UUID bobsMembership =
                jdbc.sql("SELECT id FROM memberships WHERE organization_id = ? AND user_id = ?")
                        .params(world.orgB(), world.bob().userId())
                        .query(UUID.class)
                        .single();

        HttpResponse<String> patch =
                changeRole(world.alice(), bobsMembership.toString(), "EMPLOYEE", 0);
        HttpResponse<String> delete =
                remove(world.alice(), world.orgA(), bobsMembership.toString());

        assertThat(patch.statusCode()).isEqualTo(404);
        assertThat(delete.statusCode()).isEqualTo(404);
        String role =
                jdbc.sql("SELECT role FROM memberships WHERE id = ?")
                        .param(bobsMembership)
                        .query(String.class)
                        .single();
        assertThat(role).isEqualTo("OWNER");
    }

    @Test
    void invalidBodiesAreRejectedWithFieldErrors() {
        Listed carol = find(world.alice(), "carol");
        var alice = world.alice().http();
        String path = members(world.orgA()) + "/" + carol.id();

        HttpResponse<String> unknownRole =
                alice.request(
                        "PATCH", path, "{\"role\":\"KING\",\"version\":0}", alice.csrfToken());
        HttpResponse<String> missingVersion =
                alice.request("PATCH", path, "{\"role\":\"MANAGER\"}", alice.csrfToken());

        assertThat(unknownRole.statusCode()).isEqualTo(400);
        assertThat(missingVersion.statusCode()).isEqualTo(400);
        assertThat(missingVersion.body()).contains("\"errors\"").contains("\"version\"");
    }
}
