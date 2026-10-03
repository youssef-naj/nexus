package com.l2c.nexus.shared.ratelimit;

public record RateLimitDecision(boolean allowed, long retryAfterSeconds) {

    public static final RateLimitDecision ALLOWED = new RateLimitDecision(true, 0);

    public static RateLimitDecision deniedFor(long seconds) {
        return new RateLimitDecision(false, Math.max(1, seconds));
    }
}
