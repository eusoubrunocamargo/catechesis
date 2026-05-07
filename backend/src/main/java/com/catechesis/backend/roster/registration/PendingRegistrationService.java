package com.catechesis.backend.roster.registration;

import com.catechesis.backend.common.AppProperties;
import com.catechesis.backend.common.tenancy.TenantContext;
import com.catechesis.backend.common.security.SecurityContext;
import com.catechesis.backend.klass.Klass;
import com.catechesis.backend.klass.KlassRepository;
import com.catechesis.backend.roster.child.Child;
import com.catechesis.backend.roster.child.ChildRepository;
import com.catechesis.backend.roster.child.ChildSafetyInfo;
import com.catechesis.backend.roster.child.ChildSafetyInfoRepository;
import com.catechesis.backend.roster.registration.dto.EmergencyContactRequest;
import com.catechesis.backend.roster.registration.dto.PendingRegistrationSummary;
import com.catechesis.backend.roster.registration.dto.PublicRegistrationRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PendingRegistrationService {

    private final KlassRepository klassRepository;
    private final PendingRegistrationRepository pendingRegistrationRepository;
    private final ChildRepository childRepository;
    private final ChildSafetyInfoRepository childSafetyInfoRepository;
    private final TenantContext tenantContext;
    private final SecurityContext securityContext;
    private final AppProperties appProperties;

    public PendingRegistrationService(
            KlassRepository klassRepository,
            PendingRegistrationRepository pendingRegistrationRepository,
            ChildRepository childRepository,
            ChildSafetyInfoRepository childSafetyInfoRepository,
            TenantContext tenantContext,
            SecurityContext securityContext,
            AppProperties appProperties) {
        this.klassRepository = klassRepository;
        this.pendingRegistrationRepository = pendingRegistrationRepository;
        this.childRepository = childRepository;
        this.childSafetyInfoRepository = childSafetyInfoRepository;
        this.tenantContext = tenantContext;
        this.securityContext = securityContext;
        this.appProperties = appProperties;
    }

    @Transactional
    public void submit(String slug, PublicRegistrationRequest request) {
        Klass klass = klassRepository.findByPublicSlugAndActiveTrue(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        List<EmergencyContact> contacts = request.emergencyContacts().stream()
                .map(PendingRegistrationService::toDomainContact)
                .toList();

        PendingRegistration registration = new PendingRegistration(
                UUID.randomUUID(),
                klass.getChurchId(),
                klass.getId(),
                request.childFirstName(),
                request.childLastName(),
                request.allergies(),
                contacts,
                request.parentContactEmail(),
                appProperties.consent().currentVersion(),
                Instant.now());

        pendingRegistrationRepository.save(registration);
    }

    @Transactional(readOnly = true)
    public List<PendingRegistrationSummary> listPending() {
        UUID churchId = tenantContext.requireChurchId();
        return pendingRegistrationRepository.findSummariesByChurchAndStatus(
                churchId, RegistrationStatus.PENDING);
    }

    @Transactional
    public Child approve(UUID pendingRegistrationId) {
        UUID churchId = tenantContext.requireChurchId();
        UUID reviewerId = securityContext.currentCatechistId()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        PendingRegistration pending = pendingRegistrationRepository
                .findById(pendingRegistrationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        // Cross-tenant requests get 404, not 403, to avoid leaking the
        // existence of pending registrations in other churches.
        if (!pending.getChurchId().equals(churchId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        // Service-level state check — the entity's lifecycle method
        // also enforces this, but pre-checking lets us return a clean
        // 409 without translating an IllegalStateException.
        if (pending.getStatus() != RegistrationStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }

        UUID childId = UUID.randomUUID();

        Child child = new Child(
                childId,
                pending.getChurchId(),
                pending.getKlassId(),
                pending.getChildFirstName(),
                pending.getChildLastName(),
                pending.getId());
        childRepository.save(child);

        ChildSafetyInfo safetyInfo = new ChildSafetyInfo(
                childId,
                pending.getChurchId(),
                pending.getAllergies(),
                pending.getEmergencyContacts(),
                null);
        childSafetyInfoRepository.save(safetyInfo);

        // Mutating the managed entity — Hibernate will dirty-flush on
        // transaction commit. No explicit save() needed.
        pending.approve(reviewerId);

        return child;
    }

    private static EmergencyContact toDomainContact(EmergencyContactRequest request) {
        return new EmergencyContact(request.name(), request.phone());
    }
}