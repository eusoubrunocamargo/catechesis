package com.catechesis.backend.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Project-wide configuration properties bound from {@code application.yml}
 * under the {@code app} prefix.
 *
 * <p>Currently exposes only the public base URL — the externally-visible
 * origin (scheme + host + optional port) at which the parent-facing
 * frontend will be reachable. Used by URL-assembly code such as the
 * registration link endpoint (S02-06). Future fields land here as new
 * cross-cutting configuration appears.
 *
 * <p>Bound as a record because this is a pure value object with no
 * behavior; Spring Boot supports {@code @ConfigurationProperties} on
 * records since Spring Boot 3.0.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(String publicBaseUrl) { }