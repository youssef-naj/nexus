package com.l2c.nexus.shared.id;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Generates time-ordered UUIDs (version 7): 48-bit millisecond timestamp, then random bits. */
@Component
public class TimeOrderedUuids {

    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public TimeOrderedUuids(Clock clock) {
        this.clock = clock;
    }

    public UUID next() {
        long millis = clock.millis();
        long mostSignificant = (millis << 16) | 0x7000L | (random.nextInt() & 0x0FFFL);
        long leastSignificant = (random.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(mostSignificant, leastSignificant);
    }
}
