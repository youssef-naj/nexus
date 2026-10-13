package com.l2c.nexus.identity.security;

import com.l2c.nexus.identity.application.AccountService;
import com.l2c.nexus.identity.application.AuthRateLimits;
import com.l2c.nexus.shared.ratelimit.RateLimiter;
import com.l2c.nexus.shared.security.ProblemJsonSecurityHandlers;
import com.l2c.nexus.shared.security.RequestSizeLimitFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.savedrequest.NullRequestCache;

@Configuration
@EnableWebSecurity
class SecurityConfig {

    /** The API only serves JSON: nothing may load, frame or post anywhere (ADR-0032). */
    private static final String API_CSP =
            "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'";

    private static final String PERMISSIONS_POLICY =
            "camera=(), microphone=(), geolocation=(), payment=(), usb=()";

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ProblemJsonSecurityHandlers problemHandlers,
            LoginResponseHandlers loginHandlers,
            NexusAuthenticationProvider authenticationProvider,
            LoginThrottle loginThrottle,
            RateLimiter rateLimiter,
            AuthRateLimits rateLimits,
            AccountService accountService,
            @Value("${nexus.security.secure-cookies:false}") boolean secureCookies,
            @Value("${nexus.http.max-body-bytes:102400}") long maxBodyBytes)
            throws Exception {
        // The CSRF cookie is readable by JavaScript on purpose, but it is SameSite=Lax, and Secure
        // wherever the application is served over HTTPS (nexus.security.secure-cookies)
        CookieCsrfTokenRepository csrfTokens = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokens.setCookieCustomizer(cookie -> cookie.sameSite("Lax").secure(secureCookies));

        http
                // The SPA configuration first, then our repository: spa() would override one set
                // before it
                .csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokens))
                .headers(
                        headers ->
                                headers.contentSecurityPolicy(csp -> csp.policyDirectives(API_CSP))
                                        .referrerPolicy(
                                                referrer ->
                                                        referrer.policy(ReferrerPolicy.NO_REFERRER))
                                        .addHeaderWriter(
                                                new StaticHeadersWriter(
                                                        "Permissions-Policy", PERMISSIONS_POLICY))
                                        .addHeaderWriter(
                                                new StaticHeadersWriter(
                                                        "Cross-Origin-Opener-Policy",
                                                        "same-origin"))
                                        .addHeaderWriter(
                                                new StaticHeadersWriter(
                                                        "Cross-Origin-Resource-Policy",
                                                        "same-origin")))
                .authenticationProvider(authenticationProvider)
                // First in the chain: oversized and abusive requests are rejected before any
                // expensive work (CSRF handling, password hashing)
                .addFilterBefore(new RequestSizeLimitFilter(maxBodyBytes), CsrfFilter.class)
                .addFilterBefore(
                        new AuthRateLimitFilter(loginThrottle, rateLimiter, rateLimits),
                        CsrfFilter.class)
                // Re-checks the account on every authenticated request, just before authorization
                .addFilterBefore(new ActiveAccountFilter(accountService), AuthorizationFilter.class)
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(
                                                "/api/system/ping",
                                                "/api/actuator/health",
                                                "/api/actuator/health/**")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/api/auth/register",
                                                "/api/auth/verify-email")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                // POST /api/auth/login, form-encoded fields "email" and "password". Spring handles
                // session fixation protection, CSRF token rotation and saving the context.
                .formLogin(
                        form ->
                                form.loginPage("/api/auth/login")
                                        .loginProcessingUrl("/api/auth/login")
                                        .usernameParameter("email")
                                        .passwordParameter("password")
                                        .successHandler(loginHandlers)
                                        .failureHandler(loginHandlers))
                .logout(
                        logout ->
                                logout.logoutUrl("/api/auth/logout")
                                        .logoutSuccessHandler(
                                                new HttpStatusReturningLogoutSuccessHandler(
                                                        HttpStatus.NO_CONTENT))
                                        .deleteCookies("SESSION"))
                // An API must not create a session for every anonymous request to a protected URL.
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                // ADR-0005 and ADR-0008: 401 and 403 as Problem Details, never a login redirect
                .exceptionHandling(
                        ex ->
                                ex.authenticationEntryPoint(problemHandlers)
                                        .accessDeniedHandler(problemHandlers));
        return http.build();
    }
}
