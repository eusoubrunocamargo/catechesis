package com.catechesis.backend.roster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.catechesis.backend.catechist.Catechist;
import com.catechesis.backend.catechist.CatechistRepository;
import com.catechesis.backend.catechist.CatechistRole;
import com.catechesis.backend.church.Church;
import com.catechesis.backend.church.ChurchRepository;
import com.catechesis.backend.common.slug.SlugGenerator;
import com.catechesis.backend.klass.Klass;
import com.catechesis.backend.klass.KlassRepository;
import com.catechesis.backend.roster.child.Child;
import com.catechesis.backend.roster.child.ChildRepository;
import com.catechesis.backend.roster.child.ChildSafetyInfo;
import com.catechesis.backend.roster.child.ChildSafetyInfoRepository;
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

@SpringBootTest
@Transactional
class RosterRedactionServiceIntegrationTests {

    private static final SlugGenerator SLUG_GENERATOR = new SlugGenerator();

    @Autowired RosterRedactionService rosterRedactionService;
    @Autowired ChurchRepository churchRepository;
    @Autowired CatechistRepository catechistRepository;
    @Autowired KlassRepository klassRepository;
    @Autowired PendingRegistrationRepository pendingRegistrationRepository;
    @Autowired ChildRepository childRepository;
    @Autowired ChildSafetyInfoRepository childSafetyInfoRepository;

    @PersistenceContext EntityManager entityManager;

    private Church church;
    private Klass klass;
    private Catechist reviewer;

    @BeforeEach
    void setUp() {
        church = churchRepository.save(
                new Church("Test Parish", "America/Sao_Paulo"));
        klass = klassRepository.save(new Klass(
                UUID.randomUUID(), church.getId(),
                "Test Class", SLUG_GENERATOR.generate()));
        reviewer = catechistRepository.save(new Catechist(
                church.getId(), "reviewer@example.com",
                "Test Reviewer", CatechistRole.LEAD));
    }

    @Test
    void redactsBothSafetyInfoAndOriginatingRegistration() {
        UUID pendingId = UUID.randomUUID();
        pendingRegistrationRepository.save(buildApprovedPending(pendingId));

        UUID childId = UUID.randomUUID();
        childRepository.save(buildChild(childId, pendingId));
        childSafetyInfoRepository.save(buildSafetyInfo(childId));

        rosterRedactionService.redactByChildId(childId);

        entityManager.flush();
        entityManager.clear();

        ChildSafetyInfo redactedSafety = childSafetyInfoRepository
                .findById(childId).orElseThrow();
        assertThat(redactedSafety.getAllergies()).isNull();
        assertThat(redactedSafety.getEmergencyContacts()).isEmpty();
        assertThat(redactedSafety.getNotes()).isNull();
        assertThat(redactedSafety.getSensitiveDataRedactedAt()).isNotNull();

        PendingRegistration redactedPending = pendingRegistrationRepository
                .findById(pendingId).orElseThrow();
        assertThat(redactedPending.getAllergies()).isNull();
        assertThat(redactedPending.getEmergencyContacts()).isEmpty();
        assertThat(redactedPending.getParentContactEmail()).isNull();
        assertThat(redactedPending.getSensitiveDataRedactedAt()).isNotNull();

        assertThat(redactedPending.getStatus()).isEqualTo(RegistrationStatus.APPROVED);
        assertThat(redactedPending.getChildFirstName()).isEqualTo("Gael");
        assertThat(redactedPending.getChildLastName()).isEqualTo("Silva");
        assertThat(redactedPending.getConsentVersion()).isEqualTo("v1.0.0");
        assertThat(redactedPending.getConsentGrantedAt()).isNotNull();
        assertThat(redactedPending.getReviewedByCatechistId()).isEqualTo(reviewer.getId());
        assertThat(redactedPending.getReviewedAt()).isNotNull();

        Child redactedChild = childRepository.findById(childId).orElseThrow();
        assertThat(redactedChild.getFirstName()).isEqualTo("Gael");
        assertThat(redactedChild.getLastName()).isEqualTo("Silva");
    }

    @Test
    void redactingTwiceIsIdempotent() {
        UUID pendingId = UUID.randomUUID();
        pendingRegistrationRepository.save(buildApprovedPending(pendingId));

        UUID childId = UUID.randomUUID();
        childRepository.save(buildChild(childId, pendingId));
        childSafetyInfoRepository.save(buildSafetyInfo(childId));

        rosterRedactionService.redactByChildId(childId);
        entityManager.flush();
        entityManager.clear();

        Instant firstStamp = childSafetyInfoRepository
                .findById(childId).orElseThrow().getSensitiveDataRedactedAt();

        rosterRedactionService.redactByChildId(childId);
        entityManager.flush();
        entityManager.clear();

        Instant secondStamp = childSafetyInfoRepository
                .findById(childId).orElseThrow().getSensitiveDataRedactedAt();

        assertThat(secondStamp).isEqualTo(firstStamp);
    }

    @Test
    void redactsSafetyInfoOnlyWhenNoBackReference() {
        UUID childId = UUID.randomUUID();
        childRepository.save(buildChild(childId, null));
        childSafetyInfoRepository.save(buildSafetyInfo(childId));

        rosterRedactionService.redactByChildId(childId);
        entityManager.flush();
        entityManager.clear();

        ChildSafetyInfo redactedSafety = childSafetyInfoRepository
                .findById(childId).orElseThrow();
        assertThat(redactedSafety.getAllergies()).isNull();
        assertThat(redactedSafety.getEmergencyContacts()).isEmpty();
        assertThat(redactedSafety.getSensitiveDataRedactedAt()).isNotNull();
    }

    @Test
    void throwsWhenChildDoesNotExist() {
        UUID nonexistentId = UUID.randomUUID();

        assertThatThrownBy(() ->
                rosterRedactionService.redactByChildId(nonexistentId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(nonexistentId.toString());
    }

    // ----- helpers -----

    private PendingRegistration buildApprovedPending(UUID id) {
        PendingRegistration pending = new PendingRegistration(
                id,
                church.getId(),
                klass.getId(),
                "Gael",
                "Silva",
                "Amendoim, glúten",
                List.of(new EmergencyContact("Maria Silva", "11999991234")),
                "maria@example.com",
                "v1.0.0",
                Instant.now());
        pending.approve(reviewer.getId());
        return pending;
    }

    private Child buildChild(UUID childId, UUID pendingRegistrationId) {
        return new Child(
                childId,
                church.getId(),
                klass.getId(),
                "Gael",
                "Silva",
                pendingRegistrationId);
    }

    private ChildSafetyInfo buildSafetyInfo(UUID childId) {
        return new ChildSafetyInfo(
                childId,
                church.getId(),
                "Amendoim, glúten",
                List.of(new EmergencyContact("Maria Silva", "11999991234")),
                "Asthma inhaler in backpack");
    }
}