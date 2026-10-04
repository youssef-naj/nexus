package com.l2c.nexus.team.application;

import com.l2c.nexus.membership.domain.OrgRole;

public record InvitationPreview(String organizationName, OrgRole role) {}
