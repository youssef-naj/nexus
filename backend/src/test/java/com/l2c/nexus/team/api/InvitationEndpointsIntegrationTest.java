package com.l2c.nexus.team.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.l2c.nexus.TestcontainersConfiguration;
import com.l2c.nexus.support.CrossTenantAssertions;
import com.l2c.nexus.support.TenantWorld;
import com.l2c.nexus.support.TenantWorld.Member;
import com.l2c.nexus.support.TenantWorldFactory;
import com.l2c.nexus.team.application.RecordingInvitationEmails;
import java.net.http.HttpResponse;
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
class InvitationEndpointsIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired private TenantWorldFactory factory;
    @Autowired private RecordingInvitationEmails invitationEmails;
    @Autowired private JdbcClient jdbc;

    private TenantWorld world;

    @BeforeEach
    void setUp() {
        world = factory.create(port);
    }

    private static String guest() {
        return "guest-" + UUID.randomUUID() + "@example.com";
    }

    private HttpResponse<String> invite(Member as, String email, String role) {
        return as.http()
                .postJson(
                        "/api/orgs/" + world.orgA() + "/invitations",
                        "{\"email\":\"" + email + "\",\"role\":\"" + role + "\"}",
                        as.http().csrfToken());
    }

    private HttpResponse<String> tokenCall(String action, Member as, String token) {
        return as.http()
                .postJson(
                        "/api/invitations/" + action,
                        "{\"token\":\"" + token + "\"}",
                        as.http().csrfToken());
    }

    private HttpResponse<String> accept(Member as, String token) {
        return tokenCall("accept", as, token);
    }

    private String tokenFor(String email) {
        return invitationEmails.awaitNext(email).token();
    }

    private static String idOf(HttpResponse<String> response) {
        Matcher matcher = Pattern.compile("\"id\":\"([0-9a-f-]{36})\"").matcher(response.body());
        assertThat(matcher.find()).as("an id in " + response.body()).isTrue();
        return matcher.group(1);
    }

    private HttpResponse<String> revoke(Member as, UUID org, String invitationId) {
        return as.http()
                .request(
                        "DELETE",
                        "/api/orgs/" + org + "/invitations/" + invitationId,
                        null,
                        as.http().csrfToken());
    }

    @Test
    void theInvitationsEndpointIsInvisibleToEveryoneOutsideTheOrganization() {
        CrossTenantAssertions.assertIsolated(
                world, (client, segment) -> client.get("/api/orgs/" + segment + "/invitations"));
    }

    @Test
    void anOwnerInvitesSomeoneAndTheEmailCarriesAWorkingLinkWhileOnlyTheHashIsStored() {
        String email = world.erin().email();

        HttpResponse<String> created = invite(world.alice(), email, "EMPLOYEE");

        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.body()).contains(email).contains("\"role\":\"EMPLOYEE\"");
        String token = tokenFor(email);
        assertThat(created.body()).doesNotContain(token).doesNotContain("tokenHash");
        int storedRaw =
                jdbc.sql("SELECT count(*) FROM invitations WHERE token_hash = ?")
                        .param(token)
                        .query(Integer.class)
                        .single();
        assertThat(storedRaw).isZero();
    }

    @Test
    void membersWithoutTheInvitePermissionAreRefusedAndNothingIsSent() {
        String email = guest();

        assertThat(invite(world.dan(), email, "EMPLOYEE").statusCode()).isEqualTo(403);
        assertThat(invite(world.carol(), email, "EMPLOYEE").statusCode()).isEqualTo(403);

        assertThat(invitationEmails.pollWithin(email, 1000)).isNull();
    }

    @Test
    void rolesCanOnlyBeGrantedBelowYourOwn() {
        assertThat(invite(world.ann(), guest(), "OWNER").statusCode()).isEqualTo(403);
        assertThat(invite(world.ann(), guest(), "ADMIN").statusCode()).isEqualTo(403);
        assertThat(invite(world.ann(), guest(), "MANAGER").statusCode()).isEqualTo(201);
        assertThat(invite(world.alice(), guest(), "OWNER").statusCode()).isEqualTo(201);
    }

    @Test
    void invitingAnExistingMemberIsAConflict() {
        HttpResponse<String> response = invite(world.alice(), world.dan().email(), "EMPLOYEE");

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.body()).contains("ALREADY_MEMBER");
    }

    @Test
    void reInvitingReplacesThePendingInvitation() {
        String email = world.erin().email();
        invite(world.alice(), email, "EMPLOYEE");
        String first = tokenFor(email);
        invite(world.alice(), email, "MANAGER");
        String second = tokenFor(email);

        int pending =
                jdbc.sql(
                                "SELECT count(*) FROM invitations WHERE organization_id = ?"
                                        + " AND email = ? AND status = 'PENDING'")
                        .params(world.orgA(), email)
                        .query(Integer.class)
                        .single();
        assertThat(pending).isEqualTo(1);
        assertThat(accept(world.erin(), first).statusCode()).isEqualTo(400);
        HttpResponse<String> accepted = accept(world.erin(), second);
        assertThat(accepted.statusCode()).isEqualTo(200);
        assertThat(accepted.body()).contains("\"role\":\"MANAGER\"");
    }

    @Test
    void theInvitedPersonAcceptsBecomesAMemberAndTheAcceptanceIsAudited() {
        String email = world.erin().email();
        invite(world.alice(), email, "EMPLOYEE");

        HttpResponse<String> accepted = accept(world.erin(), tokenFor(email));

        assertThat(accepted.statusCode()).isEqualTo(200);
        assertThat(accepted.body()).contains(world.orgA().toString());
        HttpResponse<String> organization = world.erin().http().get("/api/orgs/" + world.orgA());
        assertThat(organization.statusCode()).isEqualTo(200);
        assertThat(organization.body()).contains("\"role\":\"EMPLOYEE\"");
        String auditedRole =
                jdbc.sql(
                                "SELECT metadata ->> 'role' FROM audit_logs WHERE event_type ="
                                        + " 'INVITATION_ACCEPTED' AND actor_user_id = ? AND"
                                        + " organization_id = ?")
                        .params(world.erin().userId(), world.orgA())
                        .query(String.class)
                        .single();
        assertThat(auditedRole).isEqualTo("EMPLOYEE");
    }

    @Test
    void aTokenCannotBeUsedByAnotherAccountOrReplayed() {
        String email = world.erin().email();
        invite(world.alice(), email, "EMPLOYEE");
        String token = tokenFor(email);

        // Bob holds the token (say, a forwarded email) but is not the invited person
        assertThat(accept(world.bob(), token).statusCode()).isEqualTo(400);
        // ...which does not spend the invitation
        assertThat(accept(world.erin(), token).statusCode()).isEqualTo(200);
        // A second use fails
        assertThat(accept(world.erin(), token).statusCode()).isEqualTo(400);
    }

    @Test
    void expiredInvitationsCannotBeAccepted() {
        String email = world.erin().email();
        invite(world.alice(), email, "EMPLOYEE");
        String token = tokenFor(email);
        jdbc.sql(
                        "UPDATE invitations SET expires_at = now() - interval '1 hour', created_at ="
                                + " now() - interval '2 hours' WHERE organization_id = ? AND email = ?")
                .params(world.orgA(), email)
                .update();

        assertThat(accept(world.erin(), token).statusCode()).isEqualTo(400);
    }

    @Test
    void revokingStopsTheTokenAndIsScopedToTheOrganization() {
        String email = world.erin().email();
        HttpResponse<String> created = invite(world.alice(), email, "EMPLOYEE");
        String token = tokenFor(email);
        String invitationId = idOf(created);

        // Bob owns another organization: Organization A's invitation id means nothing there
        assertThat(revoke(world.bob(), world.orgB(), invitationId).statusCode()).isEqualTo(404);
        assertThat(revoke(world.bob(), world.orgA(), invitationId).statusCode()).isEqualTo(404);

        assertThat(revoke(world.alice(), world.orgA(), invitationId).statusCode()).isEqualTo(204);
        assertThat(accept(world.erin(), token).statusCode()).isEqualTo(400);
        assertThat(world.alice().http().get("/api/orgs/" + world.orgA() + "/invitations").body())
                .isEqualTo("[]");
    }

    @Test
    void anAdminCannotRevokeAnInvitationForARoleTheyCannotGrant() {
        String invitationId = idOf(invite(world.alice(), guest(), "OWNER"));

        assertThat(revoke(world.ann(), world.orgA(), invitationId).statusCode()).isEqualTo(403);
        assertThat(revoke(world.alice(), world.orgA(), invitationId).statusCode()).isEqualTo(204);
    }

    @Test
    void rejectingEndsTheInvitationWithoutCreatingAMembership() {
        String email = world.erin().email();
        invite(world.alice(), email, "EMPLOYEE");
        String token = tokenFor(email);

        assertThat(tokenCall("reject", world.erin(), token).statusCode()).isEqualTo(204);

        assertThat(accept(world.erin(), token).statusCode()).isEqualTo(400);
        assertThat(world.erin().http().get("/api/orgs/" + world.orgA()).statusCode())
                .isEqualTo(404);
    }

    @Test
    void onlyTheInvitedAccountCanPreview() {
        String email = world.erin().email();
        invite(world.alice(), email, "MANAGER");
        String token = tokenFor(email);

        HttpResponse<String> mine = tokenCall("preview", world.erin(), token);
        HttpResponse<String> other = tokenCall("preview", world.bob(), token);

        assertThat(mine.statusCode()).isEqualTo(200);
        assertThat(mine.body()).contains("\"role\":\"MANAGER\"").contains("organizationName");
        assertThat(other.statusCode()).isEqualTo(400);
    }

    @Test
    void aRevokedMemberIsReactivatedOnTheSameRowByAcceptingANewInvitation() {
        jdbc.sql(
                        "UPDATE memberships SET status = 'REVOKED' WHERE organization_id = ? AND user_id = ?")
                .params(world.orgA(), world.frank().userId())
                .update();
        assertThat(world.frank().http().get("/api/orgs/" + world.orgA()).statusCode())
                .isEqualTo(404);

        assertThat(invite(world.alice(), world.frank().email(), "MANAGER").statusCode())
                .isEqualTo(201);
        assertThat(accept(world.frank(), tokenFor(world.frank().email())).statusCode())
                .isEqualTo(200);

        String state =
                jdbc.sql(
                                "SELECT role || ':' || status FROM memberships WHERE"
                                        + " organization_id = ? AND user_id = ?")
                        .params(world.orgA(), world.frank().userId())
                        .query(String.class)
                        .single();
        assertThat(state).isEqualTo("MANAGER:ACTIVE");
        assertThat(world.frank().http().get("/api/orgs/" + world.orgA()).statusCode())
                .isEqualTo(200);
    }

    @Test
    void anonymousCallersCannotUseTheInvitationEndpoints() {
        var anonymous = world.anonymous();

        HttpResponse<String> response =
                anonymous.postJson(
                        "/api/invitations/accept", "{\"token\":\"x\"}", anonymous.csrfToken());

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void auditEventsNeverContainEmailAddressesOrTokens() {
        String email = world.erin().email();
        invite(world.alice(), email, "EMPLOYEE");
        accept(world.erin(), tokenFor(email));

        int leaks =
                jdbc.sql(
                                "SELECT count(*) FROM audit_logs WHERE organization_id = ? AND"
                                        + " event_type LIKE 'INVITATION%' AND metadata::text LIKE '%@%'")
                        .param(world.orgA())
                        .query(Integer.class)
                        .single();
        int events =
                jdbc.sql(
                                "SELECT count(*) FROM audit_logs WHERE organization_id = ? AND"
                                        + " event_type LIKE 'INVITATION%'")
                        .param(world.orgA())
                        .query(Integer.class)
                        .single();
        assertThat(leaks).isZero();
        assertThat(events).isGreaterThanOrEqualTo(2);
    }
}
