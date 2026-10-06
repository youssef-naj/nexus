package com.l2c.nexus.department.persistence;

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

/**
 * The composite foreign keys are the database's own defence of tenant boundaries: raw SQL, no
 * application code involved.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DepartmentMembershipSchemaTest {

    @Autowired private JdbcClient jdbc;

    private record Tenant(UUID organizationId, UUID membershipId, UUID departmentId) {}

    private Tenant newTenant() {
        UUID user = UUID.randomUUID();
        jdbc.sql(
                        "INSERT INTO users (id, email, password_hash, display_name, created_at,"
                                + " updated_at) VALUES (?, ?, 'x', 'Test', now(), now())")
                .params(user, "dm-" + user + "@example.com")
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

    private void assign(UUID organization, UUID department, UUID membership) {
        jdbc.sql(
                        "INSERT INTO department_memberships (organization_id, department_id,"
                                + " membership_id, created_at) VALUES (?, ?, ?, now())")
                .params(organization, department, membership)
                .update();
    }

    @Test
    void aDepartmentOfAnotherOrganizationIsRefused() {
        Tenant a = newTenant();
        Tenant b = newTenant();

        assertThatThrownBy(() -> assign(a.organizationId(), b.departmentId(), a.membershipId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_dm_department");
    }

    @Test
    void aMemberOfAnotherOrganizationIsRefused() {
        Tenant a = newTenant();
        Tenant b = newTenant();

        assertThatThrownBy(() -> assign(a.organizationId(), a.departmentId(), b.membershipId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_dm_membership");
    }

    @Test
    void aMemberCannotBeInTheSameDepartmentTwice() {
        Tenant tenant = newTenant();
        assertThatCode(
                        () ->
                                assign(
                                        tenant.organizationId(),
                                        tenant.departmentId(),
                                        tenant.membershipId()))
                .doesNotThrowAnyException();

        assertThatThrownBy(
                        () ->
                                assign(
                                        tenant.organizationId(),
                                        tenant.departmentId(),
                                        tenant.membershipId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("pk_department_memberships");
    }
}
