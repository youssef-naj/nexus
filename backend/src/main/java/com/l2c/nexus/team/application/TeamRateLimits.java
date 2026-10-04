package com.l2c.nexus.team.application;

import com.l2c.nexus.shared.ratelimit.RateLimitRule;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Bound from nexus.rate-limit.*; shares the prefix (and the email budget) with AuthRateLimits. */
@ConfigurationProperties(prefix = "nexus.rate-limit")
public record TeamRateLimits(RateLimitRule invitePerOrg, RateLimitRule emailPerAddress) {}
