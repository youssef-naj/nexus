package com.l2c.nexus.identity.application;

import java.util.UUID;

public record VerificationEmailRequested(UUID userId, String email, String displayName) {}
