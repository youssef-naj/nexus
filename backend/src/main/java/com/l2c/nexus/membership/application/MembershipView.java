package com.l2c.nexus.membership.application;

import com.l2c.nexus.membership.domain.MembershipStatus;
import com.l2c.nexus.membership.domain.OrgRole;
import java.util.UUID;

public record MembershipView(
        UUID id,
        UUID organizationId,
        UUID userId,
        OrgRole role,
        MembershipStatus status,
        long version) {}
