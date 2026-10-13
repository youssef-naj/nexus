package com.l2c.nexus.shared.system.api;

import java.time.Instant;

public record PingResponse(String status, Instant serverTime) {}
