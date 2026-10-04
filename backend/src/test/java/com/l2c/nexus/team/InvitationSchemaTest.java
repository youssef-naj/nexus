package com.l2c.nexus.team;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.l2c.nexus.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The database itself must reject invalid invitations, whatever the application does. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class InvitationSchemaTest {

    @Autowired private JdbcClient jdbc;

    private record Tenant(UUID organizationId, UUID ownerMembershipId) {}

    private Tenant newTenant() {
        UUID user = UUID.randomUUID();
        jdbc.sql(
                        "INSERT INTO users (id, email, password_hash, display_name, created_at,"
                                + " updated_at) VALUES (?, ?, 'x', 'Test', now(), now())")
                .params(user, "inv-" + user + "@example.com")
                .update();
        UUID org = UUID.randomUUID();
        jdbc.sql(
                        "INSERT INTO organizations (id, name, slug, created_at, updated_at)"
                                + " VALUES (?, 'Org', ?, now(), now())")
                .params(org, "s-" + UUID.randomUUID())
                .update();
        UUID membership = UUID.randomUUID();
        jdbc.sql(
                        "INSERT INTO memberships (id, organization_id, user_id, role, created_at,"
                                + " updated_at) VALUES (?, ?, ?, 'OWNER', now(), now())")
                .params(membership, org, user)
                .update();
        return new Tenant(org, membership);
    }

    private void insertInvitation(
            UUID org, UUID inviter, String email, String status, boolean decided) {
        String tokenHash = (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "");
        jdbc.sql(
                        "INSERT INTO invitations (id, organization_id, email, role, token_hash,"
                                + " status, invited_by_membership_id, expires_at, created_at,"
                                + " decided_at) VALUES (?, ?, ?, 'EMPLOYEE', ?, ?, ?,"
                                + " now() + interval '7 days', now(),"
                                + " CASE WHEN ? THEN now() ELSE NULL END)")
                .params(UUID.randomUUID(), org, email, tokenHash, status, inviter, decided)
                .update();
    }

    @Test
    void theInviterMustBelongToTheSameOrganization() {
        Tenant a = newTenant();
        Tenant b = newTenant();

        // Organization B's invitation, signed by Organization A's owner: the composite key refuses
        assertThatThrownBy(
                        () ->
                                insertInvitation(
                                        b.organizationId(),
                                        a.ownerMembershipId(),
                                        "x@example.com",
                                        "PENDING",
                                        false))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_invitations_inviter");
    }

    @Test
    void onlyOnePendingInvitationPerOrganizationAndEmailButHistoryIsAllowed() {
        Tenant tenant = newTenant();
        insertInvitation(
                tenant.organizationId(),
                tenant.ownerMembershipId(),
                "dup@example.com",
                "ACCEPTED",
                true);
        insertInvitation(
                tenant.organizationId(),
                tenant.ownerMembershipId(),
                "dup@example.com",
                "PENDING",
                false);

        assertThatThrownBy(
                        () ->
                                insertInvitation(
                                        tenant.organizationId(),
                                        tenant.ownerMembershipId(),
                                        "dup@example.com",
                                        "PENDING",
                                        false))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_invitations_one_pending");

        // The same address may be invited by another organization
        Tenant other = newTenant();
        assertThatCode(
                        () ->
                                insertInvitation(
                                        other.organizationId(),
                                        other.ownerMembershipId(),
                                        "dup@example.com",
                                        "PENDING",
                                        false))
                .doesNotThrowAnyException();
    }

    @Test
    void theDecisionTimeIsSetExactlyWhenTheInvitationIsNoLongerPending() {
        Tenant tenant = newTenant();

        assertThatThrownBy(
                        () ->
                                insertInvitation(
                                        tenant.organizationId(),
                                        tenant.ownerMembershipId(),
                                        "a@example.com",
                                        "PENDING",
                                        true))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_invitations_decided");
        assertThatThrownBy(
                        () ->
                                insertInvitation(
                                        tenant.organizationId(),
                                        tenant.ownerMembershipId(),
                                        "b@example.com",
                                        "ACCEPTED",
                                        false))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_invitations_decided");
    }

    @Test
    void emailsMustBeStoredInLowercase() {
        Tenant tenant = newTenant();

        assertThatThrownBy(
                        () ->
                                insertInvitation(
                                        tenant.organizationId(),
                                        tenant.ownerMembershipId(),
                                        "Ada@Example.com",
                                        "PENDING",
                                        false))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_invitations_email_lower");
    }
}
