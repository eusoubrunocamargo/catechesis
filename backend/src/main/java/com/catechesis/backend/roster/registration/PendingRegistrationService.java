package com.catechesis.backend.roster.registration;

import com.catechesis.backend.common.AppProperties;
import com.catechesis.backend.common.tenancy.TenantContext;
import com.catechesis.backend.klass.Klass;
import com.catechesis.backend.klass.KlassRepository;
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
    private final TenantContext tenantContext;
    private final AppProperties appProperties;

    public PendingRegistrationService(
            KlassRepository klassRepository,
            PendingRegistrationRepository pendingRegistrationRepository,
            TenantContext tenantContext,
            AppProperties appProperties) {
        this.klassRepository = klassRepository;
        this.pendingRegistrationRepository = pendingRegistrationRepository;
        this.tenantContext = tenantContext;
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

    private static EmergencyContact toDomainContact(EmergencyContactRequest request) {
        return new EmergencyContact(request.name(), request.phone());
    }
}