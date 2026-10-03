package com.l2c.nexus.identity.security;

import com.l2c.nexus.identity.application.AuthRateLimits;
import com.l2c.nexus.shared.ratelimit.RateLimitDecision;
import com.l2c.nexus.shared.ratelimit.RateLimiter;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Two layers: every login attempt counts against the caller's IP, and only FAILED attempts count
 * against an (email, IP) pair. Success clears the pair, so honest users are never blocked.
 */
@Component
class LoginThrottle {

    private final RateLimiter limiter;
    private final AuthRateLimits rules;

    LoginThrottle(RateLimiter limiter, AuthRateLimits rules) {
        this.limiter = limiter;
        this.rules = rules;
    }

    RateLimitDecision checkIp(String ip) {
        return limiter.tryAcquire("login:ip:" + ip, rules.loginPerIp());
    }

    RateLimitDecision checkAccount(String email, String ip) {
        return limiter.peek(failureKey(email, ip), rules.loginFailuresPerAccount());
    }

    void recordFailure(String email, String ip) {
        limiter.record(failureKey(email, ip), rules.loginFailuresPerAccount());
    }

    void recordSuccess(String email, String ip) {
        limiter.reset(failureKey(email, ip));
    }

    private static String failureKey(String email, String ip) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 320) {
            normalized = normalized.substring(0, 320); // keeps keys bounded
        }
        return "login:fail:" + normalized + "|" + ip;
    }
}
