package com.l2c.nexus.shared.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class InMemoryRateLimiterTest {

    private static final RateLimitRule TWO_PER_MINUTE = new RateLimitRule(2, Duration.ofMinutes(1));

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-01T12:00:00Z"));
    private final InMemoryRateLimiter limiter = new InMemoryRateLimiter(clock);

    @Test
    void allowsUpToTheLimitThenDenies() {
        assertThat(limiter.tryAcquire("k", TWO_PER_MINUTE).allowed()).isTrue();
        assertThat(limiter.tryAcquire("k", TWO_PER_MINUTE).allowed()).isTrue();
        assertThat(limiter.tryAcquire("k", TWO_PER_MINUTE).allowed()).isFalse();
    }

    @Test
    void retryAfterIsWhenTheOldestHitLeavesTheWindow() {
        limiter.tryAcquire("k", TWO_PER_MINUTE); // t = 0s
        clock.advance(Duration.ofSeconds(10));
        limiter.tryAcquire("k", TWO_PER_MINUTE); // t = 10s
        clock.advance(Duration.ofSeconds(10)); // t = 20s

        RateLimitDecision denied = limiter.tryAcquire("k", TWO_PER_MINUTE);

        assertThat(denied.allowed()).isFalse();
        assertThat(denied.retryAfterSeconds()).isEqualTo(40); // oldest hit expires at t = 60s
    }

    @Test
    void allowsAgainOnceTheWindowHasPassed() {
        limiter.tryAcquire("k", TWO_PER_MINUTE);
        limiter.tryAcquire("k", TWO_PER_MINUTE);
        assertThat(limiter.tryAcquire("k", TWO_PER_MINUTE).allowed()).isFalse();

        clock.advance(Duration.ofSeconds(61));

        assertThat(limiter.tryAcquire("k", TWO_PER_MINUTE).allowed()).isTrue();
    }

    @Test
    void keysAreIndependent() {
        limiter.tryAcquire("a", TWO_PER_MINUTE);
        limiter.tryAcquire("a", TWO_PER_MINUTE);

        assertThat(limiter.tryAcquire("a", TWO_PER_MINUTE).allowed()).isFalse();
        assertThat(limiter.tryAcquire("b", TWO_PER_MINUTE).allowed()).isTrue();
    }

    @Test
    void peekNeverConsumes() {
        for (int i = 0; i < 10; i++) {
            assertThat(limiter.peek("k", TWO_PER_MINUTE).allowed()).isTrue();
        }
        assertThat(limiter.tryAcquire("k", TWO_PER_MINUTE).allowed()).isTrue();
        assertThat(limiter.tryAcquire("k", TWO_PER_MINUTE).allowed()).isTrue();
    }

    @Test
    void recordedHitsCountTowardsPeek() {
        limiter.record("k", TWO_PER_MINUTE);
        assertThat(limiter.peek("k", TWO_PER_MINUTE).allowed()).isTrue();

        limiter.record("k", TWO_PER_MINUTE);

        assertThat(limiter.peek("k", TWO_PER_MINUTE).allowed()).isFalse();
    }

    @Test
    void resetForgetsTheKey() {
        limiter.record("k", TWO_PER_MINUTE);
        limiter.record("k", TWO_PER_MINUTE);

        limiter.reset("k");

        assertThat(limiter.peek("k", TWO_PER_MINUTE).allowed()).isTrue();
    }

    @Test
    void purgeRemovesExpiredKeysSoMemoryStaysBounded() {
        limiter.tryAcquire("old", TWO_PER_MINUTE);
        clock.advance(Duration.ofMinutes(2));
        limiter.tryAcquire("fresh", TWO_PER_MINUTE);

        limiter.purgeExpired();

        assertThat(limiter.trackedKeys()).isEqualTo(1);
    }
}
