package com.l2c.nexus.shared.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http, ProblemJsonSecurityHandlers problemHandlers) throws Exception {
        http
                // Cookie-to-header CSRF tokens for the SPA (XSRF-TOKEN cookie, X-XSRF-TOKEN header)
                .csrf(csrf -> csrf.spa())
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
                // ADR-0005 and ADR-0008: 401 and 403 as Problem Details, never a login redirect
                .exceptionHandling(
                        ex ->
                                ex.authenticationEntryPoint(problemHandlers)
                                        .accessDeniedHandler(problemHandlers));
        return http.build();
    }

    /**
     * Temporary until login exists (next steps). Defining this bean stops Spring Boot from creating
     * a default user with a generated password that it prints in the logs.
     */
    @Bean
    UserDetailsService noUsersYet() {
        return username -> {
            throw new UsernameNotFoundException("No accounts are configured yet");
        };
    }
}
