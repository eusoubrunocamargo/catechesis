package com.catechesis.backend.roster;

import static org.assertj.core.api.Assertions.assertThat;

import com.catechesis.backend.church.Church;
import com.catechesis.backend.church.ChurchRepository;
import com.catechesis.backend.common.slug.SlugGenerator;
import com.catechesis.backend.klass.Klass;
import com.catechesis.backend.klass.KlassRepository;
import com.catechesis.backend.roster.child.Child;
import com.catechesis.backend.roster.child.ChildRepository;
import com.catechesis.backend.roster.child.ChildSafetyInfo;
import com.catechesis.backend.roster.child.ChildSafetyInfoRepository;
import com.catechesis.backend.roster.child.ChildStatus;
import com.catechesis.backend.roster.registration.EmergencyContact;
import com.catechesis.backend.roster.registration.PendingRegistration;
import com.catechesis.backend.roster.registration.PendingRegistrationRepository;
import com.catechesis.backend.roster.registration.RegistrationStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Wiring test for the Sprint 2 roster entities. Proves that
 * {@code PendingRegistration}, {@code Child}, and {@code ChildSafetyInfo}
 * round-trip cleanly through the database — including the JSONB
 * {@code emergency_contacts} field on the two entities that carry it,
 * and the auditing-listener-managed timestamps on {@code Child}.
 *
 * <p>Each test follows the same shape: build the entity in memory,
 * save it, flush and clear the persistence context (forcing a real
 * SQL INSERT and evicting the in-memory cache), then reload via
 * {@code findById} and assert the reloaded values match.
 *
 * <p>Tests run inside {@code @Transactional} for automatic rollback;
 * the {@code catechesis_test} database is the same one used by Sprint 1's
 * integration tests.
 */
@SpringBootTest
@Transactional
class RosterEntityRoundTripIntegrationTests {

    private static final SlugGenerator SLUG_GENERATOR = new SlugGenerator();

    @Autowired ChurchRepository churchRepository;
    @Autowired KlassRepository klassRepository;
    @Autowired PendingRegistrationRepository pendingRegistrationRepository;
    @Autowired ChildRepository childRepository;
    @Autowired ChildSafetyInfoRepository childSafetyInfoRepository;

    @PersistenceContext EntityManager entityManager;

    private Church church;
    private Klass klass;

    @BeforeEach
    void setUpTenantAndKlass() {
        church = churchRepository.save(
                new Church("Smoke Test Parish", "America/Sao_Paulo"));
        klass = klassRepository.save(
                new Klass(UUID.randomUUID(), church.getId(),
                        "Smoke test class", SLUG_GENERATOR.generate()));
    }

    @Test
    void pendingRegistrationRoundTripsThroughDatabase() {
        UUID id = UUID.randomUUID();
        List<EmergencyContact> contacts = List.of(
                new EmergencyContact("Maria Silva", "+55 11 99999-1234"),
                new EmergencyContact("João Silva", "+55 11 98888-5678"));

        PendingRegistration submitted = new PendingRegistration(
                id,
                church.getId(),
                klass.getId(),
                "Gael",
                "Silva",
                "Amendoim, glúten",
                contacts,
                "maria.silva@example.com",
                "v1.0.0",
                Instant.now());

        pendingRegistrationRepository.save(submitted);

        entityManager.flush();
        entityManager.clear();

        PendingRegistration reloaded =
                pendingRegistrationRepository.findById(id).orElseThrow();

        assertThat(reloaded.getId()).isEqualTo(id);
        assertThat(reloaded.getChurchId()).isEqualTo(church.getId());
        assertThat(reloaded.getKlassId()).isEqualTo(klass.getId());
        assertThat(reloaded.getStatus()).isEqualTo(RegistrationStatus.PENDING);
        assertThat(reloaded.getChildFirstName()).isEqualTo("Gael");
        assertThat(reloaded.getChildLastName()).isEqualTo("Silva");
        assertThat(reloaded.getAllergies()).isEqualTo("Amendoim, glúten");
        assertThat(reloaded.getEmergencyContacts()).containsExactlyElementsOf(contacts);
        assertThat(reloaded.getParentContactEmail()).isEqualTo("maria.silva@example.com");
        assertThat(reloaded.getConsentVersion()).isEqualTo("v1.0.0");
        assertThat(reloaded.getConsentGrantedAt()).isNotNull();
        assertThat(reloaded.getSubmittedAt()).isNotNull();
        assertThat(reloaded.getReviewedByCatechistId()).isNull();
        assertThat(reloaded.getReviewedAt()).isNull();
        assertThat(reloaded.getRejectionReason()).isNull();
        assertThat(reloaded.getSensitiveDataRedactedAt()).isNull();
    }

    @Test
    void childRoundTripsThroughDatabase() {
        UUID childId = UUID.randomUUID();
        UUID pendingRegistrationId = persistPendingRegistration();

        Child active = new Child(
                childId,
                church.getId(),
                klass.getId(),
                "Gael",
                "Silva",
                pendingRegistrationId);

        childRepository.save(active);

        entityManager.flush();
        entityManager.clear();

        Child reloaded = childRepository.findById(childId).orElseThrow();

        assertThat(reloaded.getId()).isEqualTo(childId);
        assertThat(reloaded.getChurchId()).isEqualTo(church.getId());
        assertThat(reloaded.getKlassId()).isEqualTo(klass.getId());
        assertThat(reloaded.getFirstName()).isEqualTo("Gael");
        assertThat(reloaded.getLastName()).isEqualTo("Silva");
        assertThat(reloaded.getStatus()).isEqualTo(ChildStatus.ACTIVE);
        assertThat(reloaded.getPendingRegistrationId()).isEqualTo(pendingRegistrationId);
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void childSafetyInfoRoundTripsThroughDatabase() {
        UUID childId = UUID.randomUUID();
        UUID pendingRegistrationId = persistPendingRegistration();

        // child_safety_info FK depends on a real child row
        childRepository.save(new Child(
                childId, church.getId(), klass.getId(),
                "Gael", "Silva", pendingRegistrationId));

        List<EmergencyContact> contacts = List.of(
                new EmergencyContact("Maria Silva", "+55 11 99999-1234"));

        ChildSafetyInfo safetyInfo = new ChildSafetyInfo(
                childId,
                church.getId(),
                "Amendoim, glúten",
                contacts,
                "Usa inalador para asma");

        childSafetyInfoRepository.save(safetyInfo);

        entityManager.flush();
        entityManager.clear();

        ChildSafetyInfo reloaded =
                childSafetyInfoRepository.findById(childId).orElseThrow();

        assertThat(reloaded.getChildId()).isEqualTo(childId);
        assertThat(reloaded.getChurchId()).isEqualTo(church.getId());
        assertThat(reloaded.getAllergies()).isEqualTo("Amendoim, glúten");
        assertThat(reloaded.getEmergencyContacts()).containsExactlyElementsOf(contacts);
        assertThat(reloaded.getNotes()).isEqualTo("Usa inalador para asma");
        assertThat(reloaded.getSensitiveDataRedactedAt()).isNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }

    /**
     * Persists a minimal pending registration and returns its id, so
     * the {@link Child} tests can satisfy the optional FK back-reference.
     */
    private UUID persistPendingRegistration() {
        UUID pendingId = UUID.randomUUID();
        pendingRegistrationRepository.save(new PendingRegistration(
                pendingId,
                church.getId(),
                klass.getId(),
                "Gael",
                "Silva",
                null,
                List.of(),
                "maria.silva@example.com",
                "v1.0.0",
                Instant.now()));
        return pendingId;
    }
}