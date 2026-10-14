package com.l2c.nexus.platform.application;

import java.util.UUID;

/** A signed-in platform administrator, produced only by the platform gate. */
public record PlatformAdmin(UUID userId) {}
