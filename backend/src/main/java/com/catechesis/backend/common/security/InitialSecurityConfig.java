package com.catechesis.backend.common.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/**
 * Initial security configuration — TEMPORARY scaffolding (S01-01).
 *
 * <p>Permits all requests by default and registers the
 * {@link DevAuthenticationFilter} (when {@code app.auth.mode=dev}) so
 * that requests carrying dev auth headers populate both our custom
 * {@link SecurityContext} and Spring Security's
 * {@code SecurityContextHolder}.
 *
 * <p>Will be replaced in Sprint 5 by a real Google OAuth filter chain.
 *
 * <p>This configuration MUST NOT reach an internet-exposed environment
 * unaltered. The startup guard in Sprint 5 (per ADR-0004) is what
 * makes its existence safe.
 */
@Configuration
public class InitialSecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
                                    ObjectProvider<DevAuthenticationFilter> devAuthFilterProvider)
            throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        // Insert the dev auth filter before authorization, IF it's registered.
        // ObjectProvider lets us conditionally inject a bean that may not exist
        // (DevAuthenticationFilter has @ConditionalOnProperty).
        DevAuthenticationFilter devAuthFilter = devAuthFilterProvider.getIfAvailable();
        if (devAuthFilter != null) {
            http.addFilterBefore(devAuthFilter, AuthorizationFilter.class);
        }

        return http.build();
    }
}