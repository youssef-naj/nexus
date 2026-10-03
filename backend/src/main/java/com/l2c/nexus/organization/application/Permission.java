package com.l2c.nexus.organization.application;

import static com.l2c.nexus.membership.domain.OrgRole.ADMIN;
import static com.l2c.nexus.membership.domain.OrgRole.EMPLOYEE;
import static com.l2c.nexus.membership.domain.OrgRole.MANAGER;
import static com.l2c.nexus.membership.domain.OrgRole.OWNER;

import com.l2c.nexus.membership.domain.OrgRole;
import java.util.EnumSet;
import java.util.Set;

/**
 * Everything a member can be allowed to do inside an organization, and which roles hold it. This
 * enum is the single source of truth for the role-to-permission matrix (ADR-0007); it is documented
 * in docs/security.md. Resource-level rules (for example "not your own request") are added in the
 * services that own those resources.
 */
public enum Permission {
    ORGANIZATION_VIEW(OWNER, ADMIN, MANAGER, EMPLOYEE),
    ORGANIZATION_UPDATE(OWNER, ADMIN),
    MEMBER_VIEW(OWNER, ADMIN, MANAGER, EMPLOYEE),
    MEMBER_INVITE(OWNER, ADMIN),
    MEMBER_REVOKE(OWNER, ADMIN),
    ROLE_ASSIGN(OWNER, ADMIN),
    DEPARTMENT_VIEW(OWNER, ADMIN, MANAGER, EMPLOYEE),
    DEPARTMENT_MANAGE(OWNER, ADMIN),
    REQUEST_CREATE(OWNER, ADMIN, MANAGER, EMPLOYEE),
    REQUEST_VIEW_ALL(OWNER, ADMIN, MANAGER),
    REQUEST_REVIEW(OWNER, ADMIN, MANAGER),
    AUDIT_VIEW(OWNER, ADMIN);

    private final Set<OrgRole> heldBy;

    Permission(OrgRole first, OrgRole... others) {
        this.heldBy = EnumSet.of(first, others);
    }

    public boolean isHeldBy(OrgRole role) {
        return heldBy.contains(role);
    }
}
