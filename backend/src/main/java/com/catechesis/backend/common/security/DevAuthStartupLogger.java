package com.catechesis.backend.common.security;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Logs a prominent banner at startup when dev auth mode is active.
 * Active only when {@code app.auth.mode=dev}.
 *
 * <p>Goal: make it impossible to miss in any boot log that this
 * deployment is running with the dev authentication filter — the
 * filter that accepts header-based identity claims with no real
 * verification.
 */
@Component
@ConditionalOnProperty(name = "app.auth.mode", havingValue = "dev")
public class DevAuthStartupLogger {

    private static final Logger log = LoggerFactory.getLogger(DevAuthStartupLogger.class);

    @PostConstruct
    void announce() {
        log.warn("==========================================================");
        log.warn(" DEV AUTH MODE ACTIVE");
        log.warn(" X-Dev-Super-Admin-Id and X-Dev-Catechist-Id headers will");
        log.warn(" populate the SecurityContext WITHOUT any real verification.");
        log.warn(" Do NOT run with this configuration in production.");
        log.warn("==========================================================");
    }
}