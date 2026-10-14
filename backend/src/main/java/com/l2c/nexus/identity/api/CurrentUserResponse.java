package com.l2c.nexus.identity.api;

import java.util.UUID;

/** platformAdmin only tells the UI whether to show the Platform link; the server decides access. */
public record CurrentUserResponse(
        UUID id, String email, String displayName, boolean platformAdmin) {}
