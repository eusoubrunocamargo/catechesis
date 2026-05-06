package com.catechesis.backend.common.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/**
 * Initial security configuration — TEMPORARY scaffolding (S01-01, extended in S02-07).
 *
 * <p>Permits all requests by default and registers two filters into the chain:
 *
 * <ul>
 *   <li>{@link RateLimitFilter}: per-IP rate limit on {@code /public/**}.
 *       Runs first so flooded requests are rejected before any auth or
 *       authorization work happens.</li>
 *   <li>{@link DevAuthenticationFilter}: header-based dev auth, only
 *       registered when {@code app.auth.mode=dev} (per its
 *       {@code @ConditionalOnProperty}). Populates the security and
 *       tenant contexts before authorization.</li>
 * </ul>
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
    SecurityFilterChain filterChain(
            HttpSecurity http,
            RateLimitFilter rateLimitFilter,
            ObjectProvider<DevAuthenticationFilter> devAuthFilterProvider)
            throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        // Rate limiter runs first — before any auth work, so flooded
        // requests are rejected as cheaply as possible.
        http.addFilterBefore(rateLimitFilter, AuthorizationFilter.class);

        // Dev auth filter is conditional (app.auth.mode=dev). When present,
        // it sits between the rate limiter and authorization.
        DevAuthenticationFilter devAuthFilter = devAuthFilterProvider.getIfAvailable();
        if (devAuthFilter != null) {
            http.addFilterBefore(devAuthFilter, AuthorizationFilter.class);
        }

        return http.build();
    }

    /**
     * Disables the servlet-container auto-registration that would
     * otherwise attach {@link RateLimitFilter} to the servlet chain
     * separately from the security chain. Without this, the filter
     * would fire twice per request — once outside the security chain,
     * once inside.
     */
    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(
            RateLimitFilter rateLimitFilter) {
        FilterRegistrationBean<RateLimitFilter> registration =
                new FilterRegistrationBean<>(rateLimitFilter);
        registration.setEnabled(false);
        return registration;
    }
}