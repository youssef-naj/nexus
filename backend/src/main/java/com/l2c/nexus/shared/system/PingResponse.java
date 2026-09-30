package com.l2c.nexus.shared.system;

import java.time.Instant;

public record PingResponse(String status, Instant serverTime) {}
