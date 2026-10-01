package com.l2c.nexus.identity.api;

import java.util.UUID;

public record CurrentUserResponse(UUID id, String email, String displayName) {}
