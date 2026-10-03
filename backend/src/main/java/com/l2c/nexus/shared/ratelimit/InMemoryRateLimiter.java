package com.l2c.nexus.shared.ratelimit;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sliding-window rate limiter held in memory. Per application instance, and reset by a restart; a
 * shared store would be needed to run several instances (see ADR-0016).
 *
 * <p>Memory is bounded: each key keeps at most MAX_HITS_PER_KEY timestamps, expired keys are purged
 * every minute, and when MAX_TRACKED_KEYS is reached new keys are refused (fail closed) rather than
 * growing without limit.
 */
@Component
class InMemoryRateLimiter implements RateLimiter {

    private static final int MAX_TRACKED_KEYS = 50_000;
    private static final int MAX_HITS_PER_KEY = 1_000;
    private static final long CAPACITY_RETRY_SECONDS = 60;

    private static final class Hits {
        private final Deque<Long> times = new ArrayDeque<>();
        private long windowMillis;
    }

    private final ConcurrentHashMap<String, Hits> byKey = new ConcurrentHashMap<>();
    private final Clock clock;

    InMemoryRateLimiter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public RateLimitDecision tryAcquire(String key, RateLimitRule rule) {
        return evaluate(key, rule, true);
    }

    @Override
    public RateLimitDecision peek(String key, RateLimitRule rule) {
        return evaluate(key, rule, false);
    }

    @Override
    public void record(String key, RateLimitRule rule) {
        long now = clock.millis();
        long windowMillis = rule.window().toMillis();
        Hits hits = hitsFor(key);
        if (hits == null) {
            return;
        }
        synchronized (hits) {
            hits.windowMillis = Math.max(hits.windowMillis, windowMillis);
            prune(hits, now - windowMillis);
            add(hits, now);
        }
    }

    @Override
    public void reset(String key) {
        byKey.remove(key);
    }

    int trackedKeys() {
        return byKey.size();
    }

    @Scheduled(fixedDelay = 60_000)
    void purgeExpired() {
        long now = clock.millis();
        for (String key : byKey.keySet()) {
            byKey.computeIfPresent(
                    key,
                    (k, hits) -> {
                        synchronized (hits) {
                            prune(hits, now - hits.windowMillis);
                            return hits.times.isEmpty() ? null : hits;
                        }
                    });
        }
    }

    private RateLimitDecision evaluate(String key, RateLimitRule rule, boolean consume) {
        long now = clock.millis();
        long windowMillis = rule.window().toMillis();
        Hits hits = consume ? hitsFor(key) : byKey.get(key);
        if (hits == null) {
            // Peeking an unknown key is fine. A full table refuses new keys instead of growing.
            return consume
                    ? RateLimitDecision.deniedFor(CAPACITY_RETRY_SECONDS)
                    : RateLimitDecision.ALLOWED;
        }
        synchronized (hits) {
            hits.windowMillis = Math.max(hits.windowMillis, windowMillis);
            prune(hits, now - windowMillis);
            if (hits.times.size() >= rule.limit()) {
                long retryMillis = hits.times.peekFirst() + windowMillis - now;
                return RateLimitDecision.deniedFor(Math.ceilDiv(retryMillis, 1000));
            }
            if (consume) {
                add(hits, now);
            }
            return RateLimitDecision.ALLOWED;
        }
    }

    private Hits hitsFor(String key) {
        if (byKey.size() >= MAX_TRACKED_KEYS && !byKey.containsKey(key)) {
            purgeExpired();
            if (byKey.size() >= MAX_TRACKED_KEYS) {
                return null;
            }
        }
        return byKey.computeIfAbsent(key, k -> new Hits());
    }

    private static void prune(Hits hits, long cutoff) {
        while (!hits.times.isEmpty() && hits.times.peekFirst() <= cutoff) {
            hits.times.pollFirst();
        }
    }

    private static void add(Hits hits, long now) {
        if (hits.times.size() >= MAX_HITS_PER_KEY) {
            hits.times.pollFirst();
        }
        hits.times.addLast(now);
    }
}
