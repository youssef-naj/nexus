package com.l2c.nexus.shared.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/system/ping", "/api/actuator/health/**", "/api/actuator/health")
                        .permitAll()
                        .anyRequest().authenticated())
                // ADR-0005: unauthenticated requests receive 401, not a login redirect
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        return http.build();
    }

    /**
     * Temporary: no accounts exist until Phase 2. Defining this bean stops Spring Boot from
     * creating a default user with a generated password that it prints in the logs.
     */
    @Bean
    UserDetailsService noUsersYet() {
        return username -> {
            throw new UsernameNotFoundException("No accounts are configured yet");
        };
    }
}
