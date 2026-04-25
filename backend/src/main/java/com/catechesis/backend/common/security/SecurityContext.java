package com.catechesis.backend.common.security;

import com.catechesis.backend.catechist.CatechistRole;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only view of the authenticated principal for the active request.
 *
 * <p>Populated at request entry by an authentication filter. Cleared
 * automatically at request end.
 *
 * <p>A request can be in one of three states:
 * <ul>
 *   <li>Anonymous — neither catechist nor super-admin is set
 *   <li>Authenticated as a Catechist — catechist ID and role are set
 *   <li>Authenticated as a SuperAdmin — super-admin ID is set
 * </ul>
 * Catechist and super-admin are mutually exclusive within a single
 * request.
 */
public interface SecurityContext {

    /** True if the request is authenticated as either a catechist or super-admin. */
    boolean isAuthenticated();

    /** True if the request is authenticated as a super-admin. */
    boolean isSuperAdmin();

    /** Returns the current catechist's ID, if authenticated as a catechist. */
    Optional<UUID> currentCatechistId();

    /** Returns the current super-admin's ID, if authenticated as one. */
    Optional<UUID> currentSuperAdminId();

    /** Returns the current catechist's role, if authenticated as a catechist. */
    Optional<CatechistRole> currentRole();
}