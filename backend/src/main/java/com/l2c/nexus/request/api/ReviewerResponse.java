package com.l2c.nexus.request.api;

import com.l2c.nexus.membership.domain.OrgRole;
import java.util.UUID;

/** "you" marks the caller's own entry, so the UI can offer "assign to me". */
public record ReviewerResponse(UUID membershipId, String displayName, OrgRole role, boolean you) {}
