package com.catechesis.backend.roster.registration;

import com.catechesis.backend.roster.registration.dto.PendingRegistrationSummary;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lead-side endpoints for managing pending registrations. Approve
 * and reject endpoints land here in S02-10 / S02-11.
 *
 * <p>All endpoints in this controller are Lead-only. Tenant scoping
 * comes from {@code TenantContext} (set by {@code DevAuthenticationFilter}
 * in dev mode); responses are always filtered to the requesting Lead's
 * church.
 */
@RestController
@RequestMapping("/pending-registrations")
public class PendingRegistrationController {

    private final PendingRegistrationService pendingRegistrationService;

    public PendingRegistrationController(
            PendingRegistrationService pendingRegistrationService) {
        this.pendingRegistrationService = pendingRegistrationService;
    }

    @GetMapping
    @PreAuthorize("hasRole('LEAD')")
    public ResponseEntity<List<PendingRegistrationSummary>> listPending() {
        return ResponseEntity.ok(pendingRegistrationService.listPending());
    }
}