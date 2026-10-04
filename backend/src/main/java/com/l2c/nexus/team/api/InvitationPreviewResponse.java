package com.l2c.nexus.team.api;

import com.l2c.nexus.membership.domain.OrgRole;

public record InvitationPreviewResponse(String organizationName, OrgRole role) {}
