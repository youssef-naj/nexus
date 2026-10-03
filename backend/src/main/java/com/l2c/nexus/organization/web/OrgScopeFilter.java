package com.l2c.nexus.organization.web;

import com.l2c.nexus.organization.application.OrgAccessService;
import com.l2c.nexus.organization.application.OrgContext;
import com.l2c.nexus.organization.domain.OrganizationStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * The tenant gate (ADR-0004, ADR-0005). For every request to /api/orgs/{orgId}/... it resolves the
 * caller to an ACTIVE membership of that organization before any controller runs. Anything else
 * (not a member, no such organization, malformed id) receives the same 404. Runs after Spring
 * Security's chain, so the caller's authentication is already established.
 */
class OrgScopeFilter extends OncePerRequestFilter {

    static final String ATTRIBUTE = OrgScopeFilter.class.getName() + ".CONTEXT";

    private static final Pattern TENANT_PATH = Pattern.compile("^/api/orgs/([^/]+)(?:/.*)?$");

    private static final String NOT_FOUND =
            "{\"type\":\"about:blank\",\"title\":\"Not Found\",\"status\":404,"
                    + "\"detail\":\"The requested resource was not found.\"}";
    private static final String SUSPENDED =
            "{\"type\":\"about:blank\",\"title\":\"Organization suspended\",\"status\":403,"
                    + "\"detail\":\"This organization is suspended.\","
                    + "\"code\":\"ORGANIZATION_SUSPENDED\"}";
    private static final String UNAUTHORIZED =
            "{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401,"
                    + "\"detail\":\"Authentication is required.\"}";

    private final OrgAccessService access;

    OrgScopeFilter(OrgAccessService access) {
        this.access = access;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Matcher matcher = TENANT_PATH.matcher(request.getServletPath());
        if (!matcher.matches()) {
            chain.doFilter(request, response);
            return;
        }

        UUID userId = authenticatedUserId();
        if (userId == null) {
            write(response, 401, UNAUTHORIZED); // fail closed, even if a route was made public
            return;
        }
        UUID organizationId = parse(matcher.group(1));
        Optional<OrgContext> context =
                organizationId == null ? Optional.empty() : access.resolve(userId, organizationId);
        if (context.isEmpty()) {
            write(response, 404, NOT_FOUND);
            return;
        }
        if (context.get().status() == OrganizationStatus.SUSPENDED) {
            write(response, 403, SUSPENDED);
            return;
        }
        request.setAttribute(ATTRIBUTE, context.get());
        chain.doFilter(request, response);
    }

    private static UUID authenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return parse(authentication.getName());
    }

    private static UUID parse(String value) {
        try {
            return UUID.fromString(value);
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
