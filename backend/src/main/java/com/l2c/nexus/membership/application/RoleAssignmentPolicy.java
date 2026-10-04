package com.l2c.nexus.membership.application;

import com.l2c.nexus.membership.domain.OrgRole;
import org.springframework.stereotype.Component;

/**
 * Who may grant which role (ADR-0007): an Owner any role, an Admin only roles strictly below Admin,
 * everyone else none. Prevents role escalation.
 */
@Component
public class RoleAssignmentPolicy {

    public boolean canAssign(OrgRole actor, OrgRole target) {
        return switch (actor) {
            case OWNER -> true;
            case ADMIN -> target == OrgRole.MANAGER || target == OrgRole.EMPLOYEE;
            case MANAGER, EMPLOYEE -> false;
        };
    }
}
