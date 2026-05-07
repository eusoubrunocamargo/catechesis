package com.catechesis.backend.roster.registration;

import com.catechesis.backend.roster.registration.dto.PublicRegistrationRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, unauthenticated endpoint for parent-submitted child
 * registrations. The slug in the path identifies the target class
 * and authorizes the submission — possessing the slug is the capability.
 *
 * <p>Sits behind the {@code RateLimitFilter} (S02-07), which keys on
 * client IP. Returns {@code 201 Created} with no body on success;
 * never echoes the submitted input to avoid creating a reflection
 * surface for hostile content.
 */
@RestController
@RequestMapping("/public/classes")
public class PublicRegistrationController {

    private final PendingRegistrationService pendingRegistrationService;

    public PublicRegistrationController(
            PendingRegistrationService pendingRegistrationService) {
        this.pendingRegistrationService = pendingRegistrationService;
    }

    @PostMapping("/{slug}/registrations")
    public ResponseEntity<Void> submitRegistration(
            @PathVariable String slug,
            @Valid @RequestBody PublicRegistrationRequest request) {

        pendingRegistrationService.submit(slug, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}