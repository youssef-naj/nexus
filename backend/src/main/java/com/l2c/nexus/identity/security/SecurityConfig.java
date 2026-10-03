package com.l2c.nexus.identity.security;

import com.l2c.nexus.identity.application.AuthRateLimits;
import com.l2c.nexus.shared.ratelimit.RateLimiter;
import com.l2c.nexus.shared.security.ProblemJsonSecurityHandlers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.savedrequest.NullRequestCache;

@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ProblemJsonSecurityHandlers problemHandlers,
            LoginResponseHandlers loginHandlers,
            NexusAuthenticationProvider authenticationProvider,
            LoginThrottle loginThrottle,
            RateLimiter rateLimiter,
            AuthRateLimits rateLimits)
            throws Exception {
        http
                // Cookie-to-header CSRF tokens for the SPA (XSRF-TOKEN cookie, X-XSRF-TOKEN header)
                .csrf(csrf -> csrf.spa())
                .authenticationProvider(authenticationProvider)
                // First in the chain: abusive requests are rejected before any expensive work
                .addFilterBefore(
                        new AuthRateLimitFilter(loginThrottle, rateLimiter, rateLimits),
                        CsrfFilter.class)
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
