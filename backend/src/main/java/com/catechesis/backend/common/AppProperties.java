package com.catechesis.backend.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Project-wide configuration properties bound from {@code application.yml}
 * under the {@code app} prefix.
 *
 * <p>{@link #publicBaseUrl()} is the externally-visible origin at which
 * the parent-facing frontend is reachable. {@link #rateLimit()} groups
 * the limits enforced by {@code RateLimitFilter} on public endpoints.
 *
 * <p>Bound as records because these are pure value types with no
 * behavior. Spring Boot supports {@code @ConfigurationProperties} on
 * records since Spring Boot 3.0.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String publicBaseUrl,
        RateLimit rateLimit) {

    public record RateLimit(int publicRegistrationPerHour) { }
}