package com.l2c.nexus.organization.application;

import static com.l2c.nexus.membership.domain.OrgRole.ADMIN;
import static com.l2c.nexus.membership.domain.OrgRole.EMPLOYEE;
import static com.l2c.nexus.membership.domain.OrgRole.MANAGER;
import static com.l2c.nexus.membership.domain.OrgRole.OWNER;
import static com.l2c.nexus.organization.application.Permission.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.l2c.nexus.membership.domain.OrgRole;
import java.util.List;
import org.junit.jupiter.api.Test;

class PermissionMatrixTest {

    private final AccessPolicy policy = new AccessPolicy();

    @Test
    void employeesCanOnlyViewAndCreateRequests() {
        assertThat(policy.permissionsOf(EMPLOYEE))
                .containsExactlyInAnyOrder(
                        ORGANIZATION_VIEW, MEMBER_VIEW, DEPARTMENT_VIEW, REQUEST_CREATE);
    }

    @Test
    void managersCanReviewButNotAdministrate() {
        assertThat(policy.permissionsOf(MANAGER))
                .contains(REQUEST_REVIEW, REQUEST_VIEW_ALL)
                .doesNotContain(
                        MEMBER_INVITE,
                        MEMBER_REVOKE,
                        ROLE_ASSIGN,
                        DEPARTMENT_MANAGE,
                        ORGANIZATION_UPDATE,
                        AUDIT_VIEW);
    }

    @Test
    void adminsAdministrateMembersAndDepartments() {
        assertThat(policy.permissionsOf(ADMIN))
                .contains(
                        MEMBER_INVITE,
                        MEMBER_REVOKE,
                        ROLE_ASSIGN,
                        DEPARTMENT_MANAGE,
                        ORGANIZATION_UPDATE,
                        AUDIT_VIEW);
    }

    @Test
    void ownersHoldEveryPermission() {
        assertThat(policy.permissionsOf(OWNER)).containsExactlyInAnyOrder(Permission.values());
    }

    @Test
    void aHigherRoleNeverHasFewerPermissionsThanALowerOne() {
        List<OrgRole> descending = List.of(OWNER, ADMIN, MANAGER, EMPLOYEE);
        for (int i = 0; i < descending.size() - 1; i++) {
            assertThat(policy.permissionsOf(descending.get(i)))
                    .as(
                            descending.get(i)
                                    + " must include everything "
                                    + descending.get(i + 1)
                                    + " can do")
                    .containsAll(policy.permissionsOf(descending.get(i + 1)));
        }
    }
}
