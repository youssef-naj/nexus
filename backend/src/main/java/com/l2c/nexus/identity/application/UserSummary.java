package com.l2c.nexus.identity.application;

import java.util.UUID;

/** What other modules may know about a user. They never see the entity or the password hash. */
public record UserSummary(UUID id, String email, String displayName) {}
