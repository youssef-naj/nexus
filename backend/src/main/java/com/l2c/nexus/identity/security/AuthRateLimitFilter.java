package com.l2c.nexus.identity.security;

import com.l2c.nexus.identity.application.AuthRateLimits;
import com.l2c.nexus.shared.ratelimit.RateLimitDecision;
import com.l2c.nexus.shared.ratelimit.RateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Runs first in the security chain, so abusive requests are rejected before any CSRF check or
 * password hashing. Deliberately NOT a Spring bean: a Filter bean would be registered a second time
 * by the servlet container. It is created inside SecurityConfig instead.
 */
class AuthRateLimitFilter extends OncePerRequestFilter {

    private final LoginThrottle loginThrottle;
    private final RateLimiter limiter;
    private final AuthRateLimits rules;

    AuthRateLimitFilter(LoginThrottle loginThrottle, RateLimiter limiter, AuthRateLimits rules) {
        this.loginThrottle = loginThrottle;
        this.limiter = limiter;
        this.rules = rules;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        RateLimitDecision decision = decide(request);
        if (decision.allowed()) {
            chain.doFilter(request, response);
        } else {
            reject(response, decision);
        }
    }

    private RateLimitDecision decide(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return RateLimitDecision.ALLOWED;
        }
        // getRemoteAddr(), never X-Forwarded-For parsed by hand: that header is client-controlled.
        // Behind a reverse proxy, enable server.forward-headers-strategy for trusted proxies only.
        String ip = request.getRemoteAddr();
        return switch (request.getServletPath()) {
            case "/api/auth/login" -> checkLogin(request, ip);
            case "/api/auth/register" ->
                    limiter.tryAcquire("register:ip:" + ip, rules.registerPerIp());
            case "/api/auth/verify-email" ->
                    limiter.tryAcquire("verify:ip:" + ip, rules.verifyPerIp());
            default -> RateLimitDecision.ALLOWED;
        };
    }

    private RateLimitDecision checkLogin(HttpServletRequest request, String ip) {
        RateLimitDecision byIp = loginThrottle.checkIp(ip);
        if (!byIp.allowed()) {
            return byIp;
        }
        return loginThrottle.checkAccount(request.getParameter("email"), ip);
    }

    private static void reject(HttpServletResponse response, RateLimitDecision decision)
            throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter()
                .write(
                        "{\"type\":\"about:blank\",\"title\":\"Too many requests\","
                                + "\"status\":429,"
                                + "\"detail\":\"Too many attempts. Please try again later.\"}");
    }
}
