package com.catechesis.backend.roster.registration;

/**
 * Lifecycle status of a {@link PendingRegistration}.
 *
 * <p>Names match the values allowed by the V7 CHECK constraint
 * ({@code ck_pending_registration_status}). Persisted via
 * {@code @Enumerated(EnumType.STRING)}.
 *
 * <p>The lifecycle is deliberately one-way:
 * <ul>
 *   <li>{@link #PENDING} on initial submission</li>
 *   <li>{@link #APPROVED} once a Lead promotes the registration to a Child</li>
 *   <li>{@link #REJECTED} once a Lead declines the registration</li>
 * </ul>
 *
 * <p>There is no transition out of {@code APPROVED} or {@code REJECTED};
 * the row becomes part of the audit ledger and is never reverted.
 */
public enum RegistrationStatus {
    PENDING,
    APPROVED,
    REJECTED
}