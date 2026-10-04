package com.l2c.nexus.shared.ratelimit;

/** Thrown by services when a limit is exceeded. Rendered as 429 with Retry-After. */
public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super("Rate limit exceeded");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
