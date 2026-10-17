package com.l2c.nexus.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Catches failures that happen OUTSIDE controllers (loading the session, authenticating, account
 * checks, a database that is down) and answers with the same safe Problem Details as everywhere
 * else (ADR-0033). It sits right after the security-headers filter, so these responses carry the
 * security headers too. Not a Spring bean: created inside the security configuration.
 */
public class FailureResponseFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } catch (Exception ex) {
            Failures.Failure failure = Failures.classify(ex);
            if (failure == Failures.INTERNAL && ex instanceof IOException) {
                throw ex; // for example a client that disconnected: there is nobody to answer
            }
            String incident = Failures.report(failure, ex);
            if (response.isCommitted()) {
                throw ex; // too late to change the answer; let the container deal with it
            }
            response.resetBuffer();
            response.setStatus(failure.status());
            if (failure.retryAfterSeconds() != null) {
                response.setHeader("Retry-After", Long.toString(failure.retryAfterSeconds()));
            }
            response.setContentType("application/problem+json");
            response.setCharacterEncoding("UTF-8");
            write(response, Failures.problemJson(failure, incident));
        }
    }

    private static void write(HttpServletResponse response, String json) throws IOException {
        try {
            response.getWriter().write(json);
        } catch (IllegalStateException alreadyUsingTheStream) {
            response.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));
        }
    }
}
