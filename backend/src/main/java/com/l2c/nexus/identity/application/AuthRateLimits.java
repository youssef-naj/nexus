package com.l2c.nexus.identity.application;

import com.l2c.nexus.shared.ratelimit.RateLimitRule;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** The limits for the authentication endpoints, bound from nexus.rate-limit.* */
@ConfigurationProperties(prefix = "nexus.rate-limit")
public record AuthRateLimits(
        RateLimitRule loginPerIp,
        RateLimitRule loginFailuresPerAccount,
        RateLimitRule registerPerIp,
        RateLimitRule verifyPerIp,
        RateLimitRule emailPerAddress) {}
