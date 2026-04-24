package com.catechesis.backend.common.tenancy;

import java.util.UUID;

/*
 * Marker interface for entities that belong to a single tenant (Church).
 *
 * <p>Every tenant-scoped entity carries a {@code church_id} column and
 * implements this interface. Entities that are NOT tenant-scoped —
 * specifically {@link com.catechesis.backend.church.Church} itself and
 * {@link com.catechesis.backend.superadmin.SuperAdmin} — do not
 * implement this interface.
 *
 * <p>This interface currently has no behavioral use; it's a compile-time
 * tag that enables future tenant-scoped repositories, Hibernate filters,
 * and automated tenant-boundary audits without modifying every entity
 * at that later time.
 */
public interface TenantScoped {
    UUID getChurchId();
}