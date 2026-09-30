package com.l2c.nexus.shared.system;

import java.time.Clock;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
class SystemController {

    private final Clock clock;

    SystemController(Clock clock) {
        this.clock = clock;
    }

    @GetMapping("/ping")
    PingResponse ping() {
        return new PingResponse("ok", clock.instant());
    }
}
