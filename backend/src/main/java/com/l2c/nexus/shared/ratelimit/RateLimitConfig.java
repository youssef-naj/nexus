package com.l2c.nexus.shared.ratelimit;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables the periodic purge of expired rate-limit keys. */
@Configuration
@EnableScheduling
class RateLimitConfig {}
