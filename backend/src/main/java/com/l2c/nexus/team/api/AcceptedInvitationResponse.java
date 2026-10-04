package com.l2c.nexus.team.api;

import com.l2c.nexus.membership.domain.OrgRole;
import java.util.UUID;

public record AcceptedInvitationResponse(
        UUID organizationId, String organizationName, OrgRole role) {}
