package com.l2c.nexus.team.application;

import com.l2c.nexus.membership.domain.OrgRole;
import java.util.UUID;

public record AcceptedInvitation(UUID organizationId, String organizationName, OrgRole role) {}
