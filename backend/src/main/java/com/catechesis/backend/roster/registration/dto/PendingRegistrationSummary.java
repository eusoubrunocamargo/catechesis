package com.catechesis.backend.roster.registration.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Summary view of a {@code PendingRegistration} — the shape returned
 * by {@code GET /pending-registrations}. Deliberately excludes Tier-3
 * fields (allergies, emergency contacts) per ADR-0003: those appear
 * only in audited single-row read paths, not in list responses.
 *
 * <p>Constructed directly from a JPQL projection query — see
 * {@code PendingRegistrationRepository#findSummariesByChurchAndStatus}.
 * The DTO's component order must match the SELECT clause's column order.
 */
public record PendingRegistrationSummary(
        UUID id,
        UUID klassId,
        String klassName,
        String childFirstName,
        String childLastName,
        String parentContactEmail,
        Instant submittedAt) { }