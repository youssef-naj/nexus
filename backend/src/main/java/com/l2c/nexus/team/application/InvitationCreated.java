package com.l2c.nexus.team.application;

import com.l2c.nexus.membership.domain.OrgRole;

/** Published when an invitation is stored. Carries the raw token, so it never prints itself. */
public record InvitationCreated(
        String email, String organizationName, String inviterName, OrgRole role, String rawToken) {

    @Override
    public String toString() {
        return "InvitationCreated[redacted]";
    }
}
