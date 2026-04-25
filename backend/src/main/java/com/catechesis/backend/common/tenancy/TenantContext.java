package com.catechesis.backend.common.tenancy;

import java.util.Optional;
import java.util.UUID;

/*
 * Read-only view of the current tenant (Church) for the active request.
 *
 * <p>Populated at request entry by an authentication or tenant-resolution
 * filter. Cleared automatically at request end.
 *
 * <p>Most service-layer code should call {@link #requireChurchId()} —
 * if no tenant has been resolved, that's a programming error worth
 * throwing on. Code that legitimately may run without a tenant
 * (e.g., super-admin actions) uses {@link #churchIdOptional()}.
 */
public interface TenantContext {

    /*
     * Returns the current tenant's church ID, or empty if no tenant has
     * been resolved for this request.
     */
    Optional<UUID> churchIdOptional();

    /*
     * Returns the current tenant's church ID, or throws
     * {@link IllegalStateException} if absent. Use this in service-layer
     * code that cannot meaningfully continue without a tenant.
     */
    UUID requireChurchId();
}