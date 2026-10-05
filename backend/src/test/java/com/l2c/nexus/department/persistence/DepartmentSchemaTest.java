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

/** The database itself must reject invalid departments, whatever the application does. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DepartmentSchemaTest {

    @Autowired private JdbcClient jdbc;

    private UUID insertOrganization() {
        UUID id = UUID.randomUUID();
        jdbc.sql(
                        "INSERT INTO organizations (id, name, slug, created_at, updated_at)"
                                + " VALUES (?, 'Org', ?, now(), now())")
                .params(id, "s-" + UUID.randomUUID())
                .update();
        return id;
    }

    private void insertDepartment(UUID organization, String name) {
        jdbc.sql(
                        "INSERT INTO departments (id, organization_id, name, created_at, updated_at)"
                                + " VALUES (?, ?, ?, now(), now())")
                .params(UUID.randomUUID(), organization, name)
                .update();
    }

    @Test
    void namesAreUniquePerOrganizationIgnoringCase() {
        UUID org = insertOrganization();
        insertDepartment(org, "Engineering");

        assertThatThrownBy(() -> insertDepartment(org, "ENGINEERING"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_departments_org_name");
    }

    @Test
    void theSameNameIsAllowedInAnotherOrganization() {
        UUID first = insertOrganization();
        UUID second = insertOrganization();
        insertDepartment(first, "Engineering");

        assertThatCode(() -> insertDepartment(second, "Engineering")).doesNotThrowAnyException();
    }

    @Test
    void blankNamesAreRejected() {
        UUID org = insertOrganization();

        assertThatThrownBy(() -> insertDepartment(org, "   "))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_departments_name_not_blank");
    }
}
