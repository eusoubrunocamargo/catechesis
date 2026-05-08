package com.catechesis.backend.roster.registration.dto;

import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /pending-registrations/{id}/reject}.
 *
 * <p>The rejection reason is optional — Leads may reject without
 * elaborating. Length is bounded to match the
 * {@code rejection_reason VARCHAR(500)} column on
 * {@code pending_registration}; oversized input fails validation
 * with a 400 before reaching the service.
 */
public record RejectRegistrationRequest(

        @Size(max = 500)
        String reason) { }