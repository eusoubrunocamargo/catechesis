package com.catechesis.backend.roster.registration;

import com.catechesis.backend.roster.child.Child;
import com.catechesis.backend.roster.child.dto.ChildResponse;
import com.catechesis.backend.roster.registration.dto.PendingRegistrationSummary;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lead-side endpoints for managing pending registrations.
 *
 * <p>All endpoints are Lead-only. Tenant scoping comes from
 * {@code TenantContext}; cross-tenant requests resolve to 404 (not
 * 403) to avoid leaking the existence of pending registrations in
 * other churches.
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

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('LEAD')")
    public ResponseEntity<ChildResponse> approve(@PathVariable UUID id) {
        Child child = pendingRegistrationService.approve(id);
        return ResponseEntity.ok(ChildResponse.from(child));
    }
}