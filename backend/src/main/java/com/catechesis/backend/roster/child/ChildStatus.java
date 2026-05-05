package com.catechesis.backend.roster.child;

/**
 * Lifecycle status of a {@link Child} on the active roster.
 *
 * <p>Names match the values allowed by the V8 CHECK constraint
 * ({@code ck_child_status}). Persisted via
 * {@code @Enumerated(EnumType.STRING)}.
 *
 * <p>For Sprint 2, only {@link #ACTIVE} is ever written by application
 * code (set during the approval flow). The other values exist in the
 * enum (and the DB constraint) so that future endpoints can transition
 * a child without requiring a schema migration:
 * <ul>
 *   <li>{@link #ACTIVE} — currently enrolled</li>
 *   <li>{@link #INACTIVE} — temporarily paused (family moved, etc.);
 *       row preserved for history</li>
 *   <li>{@link #GRADUATED} — completed the catechesis cycle</li>
 * </ul>
 */
public enum ChildStatus {
    ACTIVE,
    INACTIVE,
    GRADUATED
}