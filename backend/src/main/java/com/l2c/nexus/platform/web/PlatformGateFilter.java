package com.l2c.nexus.platform.web;

import com.l2c.nexus.platform.application.PlatformAccessService;
import com.l2c.nexus.platform.application.PlatformAdmin;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * The platform gate (ADR-0006, ADR-0032): every request to /api/platform/** must come from an
 * account that holds the platform role right now, checked in the database on every request.
 * Unauthenticated callers get 401; everyone else gets the same 404 as a tenant route they do not
 * belong to, so the platform API's existence is not revealed. Fails closed, like the tenant gate.
 */
class PlatformGateFilter extends OncePerRequestFilter {

    static final String ATTRIBUTE = PlatformGateFilter.class.getName() + ".ADMIN";

    private static final Pattern PLATFORM_PATH = Pattern.compile("^/api/platform(?:/.*)?$");

    private static final String NOT_FOUND =
            "{\"type\":\"about:blank\",\"title\":\"Not Found\",\"status\":404,"
                    + "\"detail\":\"The requested resource was not found.\"}";
    private static final String UNAUTHORIZED =
            "{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401,"
                    + "\"detail\":\"Authentication is required.\"}";

    private final PlatformAccessService access;

    PlatformGateFilter(PlatformAccessService access) {
        this.access = access;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!PLATFORM_PATH.matcher(request.getServletPath()).matches()) {
            chain.doFilter(request, response);
            return;
        }
        UUID userId = authenticatedUserId();
        if (userId == null) {
            write(response, 401, UNAUTHORIZED);
            return;
        }
        Optional<PlatformAdmin> admin = access.resolve(userId);
        if (admin.isEmpty()) {
            write(response, 404, NOT_FOUND);
            return;
        }
        request.setAttribute(ATTRIBUTE, admin.get());
        chain.doFilter(request, response);
    }

    private static UUID authenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static void write(HttpServletResponse response, int status, String body)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(body);
    }
}
