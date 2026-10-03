package com.l2c.nexus.shared.ratelimit;

import java.time.Duration;
import java.util.Objects;

/** At most {@code limit} hits within any sliding {@code window}. */
public record RateLimitRule(int limit, Duration window) {

    public RateLimitRule {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1");
        }
        Objects.requireNonNull(window, "window");
        if (window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("window must be positive");
        }
    }
}
