package com.l2c.nexus.shared.ratelimit;

public interface RateLimiter {

    /** Checks the rule and, if allowed, records one hit. */
    RateLimitDecision tryAcquire(String key, RateLimitRule rule);

    /** Checks the rule without recording anything. */
    RateLimitDecision peek(String key, RateLimitRule rule);

    /** Records one hit without checking (for example a failed login). */
    void record(String key, RateLimitRule rule);

    /** Forgets all hits for the key (for example after a successful login). */
    void reset(String key);
}
