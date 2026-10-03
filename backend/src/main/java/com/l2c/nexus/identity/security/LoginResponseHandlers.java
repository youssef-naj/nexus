package com.l2c.nexus.identity.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/** Login answers: 204 on success (the SPA then calls /me), Problem Details on failure. */
@Component
class LoginResponseHandlers implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

    private static final String INVALID =
            "{\"type\":\"about:blank\",\"title\":\"Invalid credentials\",\"status\":401,"
                    + "\"detail\":\"Invalid email or password.\"}";
    private static final String UNVERIFIED =
            "{\"type\":\"about:blank\",\"title\":\"Email not verified\",\"status\":403,"
                    + "\"detail\":\"Please verify your email address before signing in.\","
                    + "\"code\":\"EMAIL_NOT_VERIFIED\"}";
    private static final String SERVER_ERROR =
            "{\"type\":\"about:blank\",\"title\":\"Internal server error\",\"status\":500,"
                    + "\"detail\":\"An unexpected error occurred.\"}";

    private final LoginThrottle throttle;

    LoginResponseHandlers(LoginThrottle throttle) {
        this.throttle = throttle;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) {
        throttle.recordSuccess(request.getParameter("email"), request.getRemoteAddr());
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception)
            throws IOException {
        if (exception instanceof EmailNotVerifiedException) {
            throttle.recordFailure(request.getParameter("email"), request.getRemoteAddr());
            write(response, 403, UNVERIFIED);
        } else if (exception instanceof InternalAuthenticationServiceException) {
            write(response, 500, SERVER_ERROR);
        } else {
            if (exception instanceof BadCredentialsException) {
                throttle.recordFailure(request.getParameter("email"), request.getRemoteAddr());
            }
            write(response, 401, INVALID);
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
