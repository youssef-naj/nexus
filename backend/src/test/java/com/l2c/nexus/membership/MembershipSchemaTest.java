package com.l2c.nexus.membership;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.l2c.nexus.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The database itself must reject invalid data, whatever the application does. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MembershipSchemaTest {

    @Autowired private JdbcClient jdbc;

    private UUID insertUser() {
        UUID id = UUID.randomUUID();
        jdbc.sql(
                        "INSERT INTO users (id, email, password_hash, display_name, created_at,"
                                + " updated_at) VALUES (?, ?, 'x', 'Test', now(), now())")
                .params(id, "schema-" + id + "@example.com")
                .update();
        return id;
    }

    private UUID insertOrganization(String slug) {
        UUID id = UUID.randomUUID();
        jdbc.sql(
                        "INSERT INTO organizations (id, name, slug, created_at, updated_at)"
                                + " VALUES (?, 'Org', ?, now(), now())")
                .params(id, slug)
                .update();
        return id;
    }

    private void insertMembership(UUID organization, UUID user, String role) {
        jdbc.sql(
                        "INSERT INTO memberships (id, organization_id, user_id, role, created_at,"
                                + " updated_at) VALUES (?, ?, ?, ?, now(), now())")
                .params(UUID.randomUUID(), organization, user, role)
                .update();
    }

    @Test
    void aUserCannotJoinTheSameOrganizationTwice() {
        UUID user = insertUser();
        UUID org = insertOrganization("s-" + UUID.randomUUID());
        insertMembership(org, user, "OWNER");

        assertThatThrownBy(() -> insertMembership(org, user, "EMPLOYEE"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_memberships_org_user");
    }

    @Test
    void onlyKnownRolesAreAccepted() {
        UUID user = insertUser();
        UUID org = insertOrganization("s-" + UUID.randomUUID());

        assertThatThrownBy(() -> insertMembership(org, user, "KING"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_memberships_role");
    }

    @Test
    void aMembershipMustPointAtARealOrganization() {
        UUID user = insertUser();

        assertThatThrownBy(() -> insertMembership(UUID.randomUUID(), user, "OWNER"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_memberships_organization");
    }

    @Test
    void slugsMustBeLowercaseHyphenatedWords() {
        assertThatThrownBy(() -> insertOrganization("Bad Slug"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_organizations_slug_format");
    }
}
