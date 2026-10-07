package com.l2c.nexus.request.application;

import com.l2c.nexus.shared.ratelimit.RateLimitRule;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Bound from nexus.rate-limit.*; shares the prefix with the other limit records. */
@ConfigurationProperties(prefix = "nexus.rate-limit")
public record RequestRateLimits(RateLimitRule requestCreatePerMember) {}
