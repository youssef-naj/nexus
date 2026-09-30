package com.l2c.nexus.shared.system;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class SystemControllerTest {

    @Test
    void pingReportsOkAndUsesInjectedClock() {
        Instant fixed = Instant.parse("2026-09-30T10:15:30Z");
        SystemController controller = new SystemController(Clock.fixed(fixed, ZoneOffset.UTC));

        PingResponse response = controller.ping();

        assertThat(response.status()).isEqualTo("ok");
        assertThat(response.serverTime()).isEqualTo(fixed);
    }
}
