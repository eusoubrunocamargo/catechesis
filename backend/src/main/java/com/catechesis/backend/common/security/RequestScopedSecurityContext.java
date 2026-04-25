package com.catechesis.backend.common.security;

import com.catechesis.backend.catechist.CatechistRole;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

/**
 * Default implementation of {@link SecurityContext}. Request-scoped:
 * a fresh instance per HTTP request, populated by an authentication
 * filter and read by controllers/services.
 */
@Component
@Scope(value = WebApplicationContext.SCOPE_REQUEST,
        proxyMode = ScopedProxyMode.TARGET_CLASS)
public class RequestScopedSecurityContext implements SecurityContext {

    private UUID catechistId;
    private UUID superAdminId;
    private CatechistRole role;

    /**
     * Marks the current request as authenticated as a Catechist.
     * Called by the authentication filter only.
     */
    public void authenticateAsCatechist(UUID catechistId, CatechistRole role) {
        this.catechistId = catechistId;
        this.role = role;
        this.superAdminId = null;  // mutual exclusion
    }

    /**
     * Marks the current request as authenticated as a SuperAdmin.
     * Called by the authentication filter only.
     */
    public void authenticateAsSuperAdmin(UUID superAdminId) {
        this.superAdminId = superAdminId;
        this.catechistId = null;   // mutual exclusion
        this.role = null;
    }

    @Override
    public boolean isAuthenticated() {
        return catechistId != null || superAdminId != null;
    }

    @Override
    public boolean isSuperAdmin() {
        return superAdminId != null;
    }

    @Override
    public Optional<UUID> currentCatechistId() {
        return Optional.ofNullable(catechistId);
    }

    @Override
    public Optional<UUID> currentSuperAdminId() {
        return Optional.ofNullable(superAdminId);
    }

    @Override
    public Optional<CatechistRole> currentRole() {
        return Optional.ofNullable(role);
    }
}