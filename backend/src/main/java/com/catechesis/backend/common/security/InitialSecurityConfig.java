package com.catechesis.backend.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Initial permissive security configuration — TEMPORARY.
 *
 * <p>Placeholder until Sprint 1 item S01-08 installs the real security
 * seam ({@code DevAuthenticationFilter} in dev mode, Google OAuth2 in
 * prod) per ADR-0004.
 *
 * <p>This configuration permits all requests without authentication.
 * It MUST NOT reach any environment exposed to the internet. A startup
 * guard (added in Sprint 5) will refuse to boot in the {@code prod}
 * profile while {@code app.auth.mode=dev} — that guard is what makes
 * the existence of this file safe.
 */
@Configuration
public class InitialSecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}