package com.catechesis.backend.roster.registration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request-side DTO for one emergency contact in a public registration
 * submission.
 *
 * <p>Validation annotations live here, on the input boundary. The
 * domain value type {@code EmergencyContact} carries no validation —
 * by the time an instance exists in the domain layer, its fields are
 * either freshly validated input or DB-loaded data already proven
 * good on the way in.
 */
public record EmergencyContactRequest(

        @NotBlank
        @Size(max = 200)
        String name,

        @NotBlank
        @Size(max = 50)
        String phone) { }