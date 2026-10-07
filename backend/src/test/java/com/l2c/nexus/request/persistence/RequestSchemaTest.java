package com.l2c.nexus.request.persistence;

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

/** The database itself must refuse cross-tenant and invalid requests, whatever the code does. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RequestSchemaTest {

    @Autowired private JdbcClient jdbc;

    private record Tenant(UUID organizationId, UUID membershipId, UUID departmentId) {}

    private Tenant newTenant() {
        UUID user = UUID.randomUUID();
        jdbc.sql(
                        "INSERT INTO users (id, email, password_hash, display_name, created_at,"
                                + " updated_at) VALUES (?, ?, 'x', 'Test', now(), now())")
                .params(user, "rs-" + user + "@example.com")
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
        UUID department = UUID.randomUUID();
        jdbc.sql(
                        "INSERT INTO departments (id, organization_id, name, created_at, updated_at)"
                                + " VALUES (?, ?, 'Dept', now(), now())")
                .params(department, org)
                .update();
        return new Tenant(org, membership, department);
    }

    private void insertRequest(
            UUID organization,
            String reference,
            String title,
            String category,
            String status,
            UUID creator,
            UUID assignee,
            UUID department) {
        jdbc.sql(
                        "INSERT INTO service_requests (id, organization_id, reference, title,"
                                + " category, status, created_by_membership_id,"
                                + " assignee_membership_id, department_id, created_at, updated_at)"
                                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())")
                .params(
                        UUID.randomUUID(),
                        organization,
                        reference,
                        title,
                        category,
                        status,
                        creator,
                        assignee,
                        department)
                .update();
    }

    private void valid(Tenant tenant, String reference) {
        insertRequest(
                tenant.organizationId(),
                reference,
                "Title",
                "HR",
                "DRAFT",
                tenant.membershipId(),
                null,
                null);
    }

    @Test
    void theCreatorMustBelongToTheSameOrganization() {
        Tenant a = newTenant();
        Tenant b = newTenant();

        assertThatThrownBy(
                        () ->
                                insertRequest(
                                        b.organizationId(),
                                        "REQ-000001",
                                        "T",
                                        "HR",
                                        "DRAFT",
                                        a.membershipId(),
                                        null,
                                        null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_requests_creator");
    }

    @Test
    void theDepartmentMustBelongToTheSameOrganization() {
        Tenant a = newTenant();
        Tenant b = newTenant();

        assertThatThrownBy(
                        () ->
                                insertRequest(
                                        a.organizationId(),
                                        "REQ-000001",
                                        "T",
                                        "HR",
                                        "DRAFT",
                                        a.membershipId(),
                                        null,
                                        b.departmentId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_requests_department");
    }

    @Test
    void theAssigneeMustBelongToTheSameOrganization() {
        Tenant a = newTenant();
        Tenant b = newTenant();

        assertThatThrownBy(
                        () ->
                                insertRequest(
                                        a.organizationId(),
                                        "REQ-000001",
                                        "T",
                                        "HR",
                                        "DRAFT",
                                        a.membershipId(),
                                        b.membershipId(),
                                        null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_requests_assignee");
    }

    @Test
    void assigneeAndDepartmentAreOptionalButValidWhenGiven() {
        Tenant tenant = newTenant();

        assertThatCode(() -> valid(tenant, "REQ-000001")).doesNotThrowAnyException();
        assertThatCode(
                        () ->
                                insertRequest(
                                        tenant.organizationId(),
                                        "REQ-000002",
                                        "T",
                                        "HR",
                                        "DRAFT",
                                        tenant.membershipId(),
                                        tenant.membershipId(),
                                        tenant.departmentId()))
                .doesNotThrowAnyException();
    }

    @Test
    void referencesAreUniquePerOrganizationButNotAcrossOrganizations() {
        Tenant a = newTenant();
        Tenant b = newTenant();
        valid(a, "REQ-000001");

        assertThatThrownBy(() -> valid(a, "REQ-000001"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_requests_org_reference");
        assertThatCode(() -> valid(b, "REQ-000001")).doesNotThrowAnyException();
    }

    @Test
    void onlyKnownCategoriesAndStatusesAreAccepted() {
        Tenant tenant = newTenant();

        assertThatThrownBy(
                        () ->
                                insertRequest(
                                        tenant.organizationId(),
                                        "REQ-000001",
                                        "T",
                                        "GARDENING",
                                        "DRAFT",
                                        tenant.membershipId(),
                                        null,
                                        null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_requests_category");
        assertThatThrownBy(
                        () ->
                                insertRequest(
                                        tenant.organizationId(),
                                        "REQ-000002",
                                        "T",
                                        "HR",
                                        "ESCALATED",
                                        tenant.membershipId(),
                                        null,
                                        null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_requests_status");
    }

    @Test
    void blankTitlesAreRejected() {
        Tenant tenant = newTenant();

        assertThatThrownBy(
                        () ->
                                insertRequest(
                                        tenant.organizationId(),
                                        "REQ-000001",
                                        "   ",
                                        "HR",
                                        "DRAFT",
                                        tenant.membershipId(),
                                        null,
                                        null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_requests_title_not_blank");
    }
}
