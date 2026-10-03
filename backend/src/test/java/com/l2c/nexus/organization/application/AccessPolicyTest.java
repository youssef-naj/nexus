package com.l2c.nexus.organization.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.l2c.nexus.membership.domain.OrgRole;
import com.l2c.nexus.organization.domain.OrganizationStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class AccessPolicyTest {

    private final AccessPolicy policy = new AccessPolicy();

    private static OrgContext as(OrgRole role) {
        return new OrgContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                role,
                OrganizationStatus.ACTIVE);
    }

    @Test
    void requireThrowsWhenTheRoleLacksThePermission() {
        assertThatThrownBy(() -> policy.require(as(OrgRole.EMPLOYEE), Permission.MEMBER_INVITE))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requirePassesWhenThePermissionIsHeld() {
        assertThatCode(() -> policy.require(as(OrgRole.ADMIN), Permission.MEMBER_INVITE))
                .doesNotThrowAnyException();
    }
}
