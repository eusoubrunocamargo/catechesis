package com.catechesis.backend.roster.registration.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Request body for {@code POST /public/classes/{slug}/registrations}.
 *
 * <p>All inputs from an unauthenticated parent. Bean Validation
 * enforces shape and size before anything reaches the service layer;
 * any violation produces a 400 response with field-level error details
 * (but no echo of the submitted values — Spring's default validation
 * response shape names the failing field, not its value).
 *
 * <p>Consent is captured as a boolean here; the consent version itself
 * is a server-controlled property not present in the request.
 */
public record PublicRegistrationRequest(

        @NotBlank
        @Size(max = 200)
        String childFirstName,

        @NotBlank
        @Size(max = 200)
        String childLastName,

        @Size(max = 5000)
        String allergies,

        @NotEmpty
        @Valid
        List<EmergencyContactRequest> emergencyContacts,

        @NotBlank
        @Email
        @Size(max = 200)
        String parentContactEmail,

        @AssertTrue(message = "consent must be accepted")
        boolean consentAccepted) { }