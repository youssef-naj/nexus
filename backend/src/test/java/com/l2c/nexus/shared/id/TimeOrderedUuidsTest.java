package com.l2c.nexus.shared.id;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TimeOrderedUuidsTest {

    private static final Instant NOW = Instant.parse("2026-10-03T12:00:00Z");

    private final TimeOrderedUuids ids = new TimeOrderedUuids(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void hasVersion7AndTheStandardVariant() {
        UUID id = ids.next();

        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    void theFirst48BitsAreTheTimestamp() {
        UUID id = ids.next();

        assertThat(id.getMostSignificantBits() >>> 16).isEqualTo(NOW.toEpochMilli());
    }

    @Test
    void identifiersDoNotRepeat() {
        Set<UUID> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            assertThat(seen.add(ids.next())).isTrue();
        }
    }
}
