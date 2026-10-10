package com.l2c.nexus.request.persistence;

import static org.assertj.core.api.Assertions.assertThat;
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

/** The database itself keeps assignment events consistent and inside one organization. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RequestEventTargetSchemaTest {

    @Autowired private JdbcClient jdbc;

    private record Tenant(UUID organizationId, UUID membershipId, UUID requestId) {}

    private Tenant newTenant() {
        UUID user = UUID.randomUUID();
        jdbc.sql(
                        "INSERT INTO users (id, email, password_hash, display_name, created_at,"
                                + " updated_at) VALUES (?, ?, 'x', 'Test', now(), now())")
                .params(user, "rt-" + user + "@example.com")
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

    private void insertEvent(Tenant tenant, String action, UUID target) {
        jdbc.sql(
                        "INSERT INTO request_events (id, organization_id, request_id,"
                                + " actor_membership_id, action, from_status, to_status,"
                                + " target_membership_id, occurred_at)"
                                + " VALUES (?, ?, ?, ?, ?, 'SUBMITTED', 'SUBMITTED', ?, now())")
                .params(
                        UUID.randomUUID(),
                        tenant.organizationId(),
                        tenant.requestId(),
                        tenant.membershipId(),
                        action,
                        target)
                .update();
    }

    @Test
    void assignmentEventsAreAcceptedWithATargetInTheSameOrganization() {
        Tenant tenant = newTenant();

        assertThatCode(() -> insertEvent(tenant, "ASSIGN", tenant.membershipId()))
                .doesNotThrowAnyException();
        assertThatCode(() -> insertEvent(tenant, "UNASSIGN", tenant.membershipId()))
                .doesNotThrowAnyException();
    }

    @Test
    void assignmentEventsMustNameATargetAndTransitionsMustNot() {
        Tenant tenant = newTenant();

        assertThatThrownBy(() -> insertEvent(tenant, "ASSIGN", null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_request_events_target_matches_action");
        assertThatThrownBy(() -> insertEvent(tenant, "APPROVE", tenant.membershipId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_request_events_target_matches_action");
    }

    @Test
    void theTargetMustBelongToTheSameOrganization() {
        Tenant a = newTenant();
        Tenant b = newTenant();

        assertThatThrownBy(() -> insertEvent(a, "ASSIGN", b.membershipId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_request_events_target");
    }

    @Test
    void theIndexBehindAssignedToMeExists() {
        int count =
                jdbc.sql(
                                "SELECT count(*) FROM pg_indexes WHERE indexname = 'ix_requests_org_assignee_status'")
                        .query(Integer.class)
                        .single();

        assertThat(count).isEqualTo(1);
    }
}
