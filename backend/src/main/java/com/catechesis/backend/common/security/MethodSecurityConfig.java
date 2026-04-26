package com.catechesis.backend.common.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Activates {@code @PreAuthorize} / {@code @PostAuthorize} support on
 * service and controller methods. Without this, those annotations are
 * silently ignored.
 */
@Configuration
@EnableMethodSecurity
public class MethodSecurityConfig {
}