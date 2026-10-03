package com.l2c.nexus.organization.application;

import com.l2c.nexus.membership.domain.OrgRole;
import java.util.Arrays;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class AccessPolicy {

    public boolean can(OrgRole role, Permission permission) {
        return permission.isHeldBy(role);
    }

    /** Throws AccessDeniedException, which the security layer turns into a 403 Problem. */
    public void require(OrgContext context, Permission permission) {
        if (!can(context.role(), permission)) {
            throw new AccessDeniedException(
                    "Role " + context.role() + " lacks permission " + permission);
        }
    }

    /** For the UI to hide buttons. Never a substitute for server-side checks. */
    public List<Permission> permissionsOf(OrgRole role) {
        return Arrays.stream(Permission.values()).filter(p -> p.isHeldBy(role)).toList();
    }
}
