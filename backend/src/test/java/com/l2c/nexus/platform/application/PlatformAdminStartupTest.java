package com.l2c.nexus.platform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class PlatformAdminStartupTest {

    @Test
    void parsesTrimsLowercasesAndDeduplicates() {
        assertThat(
                        PlatformAdminStartup.parse(
                                " Ada@Example.com , ada@example.com,Bob@Example.COM "))
                .containsExactly("ada@example.com", "bob@example.com");
    }

    @Test
    void ignoresBlankEntriesAndAnEmptyValue() {
        assertThat(PlatformAdminStartup.parse("")).isEmpty();
        assertThat(PlatformAdminStartup.parse(" , ,, ")).isEmpty();
    }

    @Test
    void refusesMoreThanTwentyAddresses() {
        String tooMany =
                IntStream.rangeClosed(1, PlatformAdminStartup.MAX_ADMINISTRATORS + 1)
                        .mapToObj(i -> "admin" + i + "@example.com")
                        .collect(Collectors.joining(","));

        assertThatThrownBy(() -> PlatformAdminStartup.parse(tooMany))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("more than 20");
    }
}
