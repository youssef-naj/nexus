package com.l2c.nexus.request.persistence;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.l2c.nexus.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The database itself refuses cross-tenant history and rewrites of history. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RequestEventSchemaTest {

    @Autowired private JdbcClient jdbc;

    private record Tenant(UUID organizationId, UUID membershipId, UUID requestId) {}

    private Tenant newTenant() {
        UUID user = UUID.randomUUID();
        jdbc.sql(
                        "INSERT INTO users (id, email, password_hash, display_name, created_at,"
                                + " updated_at) VALUES (?, ?, 'x', 'Test', now(), now())")
                .params(user, "re-" + user + "@example.com")
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
        UUID request = UUID.randomUUID();
        jdbc.sql(
                        "INSERT INTO service_requests (id, organization_id, reference, title,"
                                + " category, status, created_by_membership_id, created_at,"
                                + " updated_at) VALUES (?, ?, 'REQ-000001', 'T', 'HR', 'SUBMITTED',"
                                + " ?, now(), now())")
                .params(request, org, membership)
                .update();
        return new Tenant(org, membership, request);
    }

    private void insertEvent(
            UUID org, UUID request, UUID actor, String action, String from, String comment) {
        jdbc.sql(
                        "INSERT INTO request_events (id, organization_id, request_id,"
                                + " actor_membership_id, action, from_status, to_status, comment,"
                                + " occurred_at) VALUES (?, ?, ?, ?, ?, ?, 'APPROVED', ?, now())")
                .params(UUID.randomUUID(), org, request, actor, action, from, comment)
                .update();
    }

    private void validEvent(Tenant tenant) {
        insertEvent(
                tenant.organizationId(),
                tenant.requestId(),
                tenant.membershipId(),
                "APPROVE",
                "SUBMITTED",
                "ok");
    }

    @Test
    void theRequestMustBelongToTheSameOrganization() {
        Tenant a = newTenant();
        Tenant b = newTenant();

        assertThatThrownBy(
                        () ->
                                insertEvent(
                                        a.organizationId(),
                                        b.requestId(),
                                        a.membershipId(),
                                        "APPROVE",
                                        "SUBMITTED",
                                        null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_request_events_request");
    }

    @Test
    void theActorMustBelongToTheSameOrganization() {
        Tenant a = newTenant();
        Tenant b = newTenant();

        assertThatThrownBy(
                        () ->
                                insertEvent(
                                        a.organizationId(),
                                        a.requestId(),
                                        b.membershipId(),
                                        "APPROVE",
                                        "SUBMITTED",
                                        null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_request_events_actor");
    }

    @Test
    void onlyKnownActionsAndStatusesAreAccepted() {
        Tenant tenant = newTenant();

        assertThatThrownBy(
                        () ->
                                insertEvent(
                                        tenant.organizationId(),
                                        tenant.requestId(),
                                        tenant.membershipId(),
                                        "DELETE",
                                        "SUBMITTED",
                                        null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_request_events_action");
        assertThatThrownBy(
                        () ->
                                insertEvent(
                                        tenant.organizationId(),
                                        tenant.requestId(),
                                        tenant.membershipId(),
                                        "APPROVE",
                                        "ESCALATED",
                                        null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_request_events_from_status");
    }

    @Test
    void blankCommentsAreRejectedButMissingOnesAreFine() {
        Tenant tenant = newTenant();

        assertThatThrownBy(
                        () ->
                                insertEvent(
                                        tenant.organizationId(),
                                        tenant.requestId(),
                                        tenant.membershipId(),
                                        "APPROVE",
                                        "SUBMITTED",
                                        "   "))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_request_events_comment_not_blank");
        assertThatCode(
                        () ->
                                insertEvent(
                                        tenant.organizationId(),
                                        tenant.requestId(),
                                        tenant.membershipId(),
                                        "APPROVE",
                                        "SUBMITTED",
                                        null))
                .doesNotThrowAnyException();
    }

    @Test
    void eventsCannotBeUpdated() {
        validEvent(newTenant());

        assertThatThrownBy(() -> jdbc.sql("UPDATE request_events SET comment = 'forged'").update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    void eventsCannotBeDeleted() {
        validEvent(newTenant());

        assertThatThrownBy(() -> jdbc.sql("DELETE FROM request_events").update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    void theTableCannotBeTruncated() {
        assertThatThrownBy(() -> jdbc.sql("TRUNCATE request_events").update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }
}
