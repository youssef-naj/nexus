package com.l2c.nexus.identity.security;

import com.l2c.nexus.identity.application.AccountService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * A session proves who logged in earlier, not that the account is still allowed in. On every
 * authenticated request this re-checks the account (one primary-key lookup); a disabled account has
 * its session destroyed and the request continues unauthenticated, so it receives a 401. Not a
 * Spring bean: it is created inside SecurityConfig, like AuthRateLimitFilter.
 */
class ActiveAccountFilter extends OncePerRequestFilter {

    private final AccountService accounts;

    ActiveAccountFilter(AccountService accounts) {
        this.accounts = accounts;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof AuthenticatedUser user
                && accounts.findActiveUser(user.id()).isEmpty()) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            SecurityContextHolder.clearContext();
        }
        chain.doFilter(request, response);
    }
}
