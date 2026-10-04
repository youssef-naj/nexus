package com.l2c.nexus.membership.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.l2c.nexus.membership.domain.OrgRole;
import org.junit.jupiter.api.Test;

class RoleAssignmentPolicyTest {

    private final RoleAssignmentPolicy policy = new RoleAssignmentPolicy();

    @Test
    void anOwnerCanGrantEveryRole() {
        for (OrgRole role : OrgRole.values()) {
            assertThat(policy.canAssign(OrgRole.OWNER, role)).as("OWNER grants " + role).isTrue();
        }
    }

    @Test
    void anAdminCanOnlyGrantRolesStrictlyBelowAdmin() {
        assertThat(policy.canAssign(OrgRole.ADMIN, OrgRole.MANAGER)).isTrue();
        assertThat(policy.canAssign(OrgRole.ADMIN, OrgRole.EMPLOYEE)).isTrue();
        assertThat(policy.canAssign(OrgRole.ADMIN, OrgRole.ADMIN)).isFalse();
        assertThat(policy.canAssign(OrgRole.ADMIN, OrgRole.OWNER)).isFalse();
    }

    @Test
    void managersAndEmployeesCannotGrantAnything() {
        for (OrgRole role : OrgRole.values()) {
            assertThat(policy.canAssign(OrgRole.MANAGER, role)).isFalse();
            assertThat(policy.canAssign(OrgRole.EMPLOYEE, role)).isFalse();
        }
    }
}
