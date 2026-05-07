package com.catechesis.backend.roster.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.catechesis.backend.roster.child.ChildStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for {@code POST /pending-registrations/{id}/approve}.
 *
 * <p>Covers happy path (approval creates Child + ChildSafetyInfo, flips
 * the registration's status), conflict cases (already-approved,
 * already-rejected), tenant isolation (cross-church Lead gets 404),
 * and authorization (non-Lead is forbidden).
 *
 * <p>The atomicity-on-failure property is covered separately in S02-13's
 * public-surface security tests; here we trust {@code @Transactional}
 * to behave as documented.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PendingRegistrationApprovalIntegrationTests {

    private static final SlugGenerator SLUG_GENERATOR = new SlugGenerator();

    @Autowired MockMvc mockMvc;
    @Autowired ChurchRepository churchRepository;
    @Autowired CatechistRepository catechistRepository;
    @Autowired KlassRepository klassRepository;
    @Autowired PendingRegistrationRepository pendingRegistrationRepository;
    @Autowired ChildRepository childRepository;
    @Autowired ChildSafetyInfoRepository childSafetyInfoRepository;

    private Catechist leadInA;
    private Catechist plainCatechistInA;
    private Catechist leadInB;
    private Klass klassInA;

    @BeforeEach
    void setUp() {
        Church churchA = churchRepository.save(
                new Church("Parish A", "America/Sao_Paulo"));
        Church churchB = churchRepository.save(
                new Church("Parish B", "America/Sao_Paulo"));

        leadInA = catechistRepository.save(new Catechist(
                churchA.getId(), "lead-a@example.com", "Lead A", CatechistRole.LEAD));
        plainCatechistInA = catechistRepository.save(new Catechist(
                churchA.getId(), "catechist-a@example.com", "Catechist A",
                CatechistRole.CATECHIST));
        leadInB = catechistRepository.save(new Catechist(
                churchB.getId(), "lead-b@example.com", "Lead B", CatechistRole.LEAD));

        klassInA = klassRepository.save(new Klass(
                UUID.randomUUID(), churchA.getId(),
                "Sementinha A", SLUG_GENERATOR.generate()));
    }

    @Test
    void leadApprovesPendingRegistrationCreatingChildAndSafetyInfo() throws Exception {
        PendingRegistration pending = persistPending(
                klassInA.getChurchId(), klassInA.getId(),
                "Gael", "Silva",
                "Amendoim, glúten",
                List.of(new EmergencyContact("Maria Silva", "+55 11 99999-1234")));

        mockMvc.perform(post("/pending-registrations/{id}/approve", pending.getId())
                        .header("X-Dev-Catechist-Id", leadInA.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Gael"))
                .andExpect(jsonPath("$.lastName").value("Silva"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                // Tier-3 fields must not appear in the approval response
                .andExpect(jsonPath("$.allergies").doesNotExist())
                .andExpect(jsonPath("$.emergencyContacts").doesNotExist());

        // Pending registration is now APPROVED with reviewer + timestamp
        PendingRegistration after = pendingRegistrationRepository
                .findById(pending.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(RegistrationStatus.APPROVED);
        assertThat(after.getReviewedByCatechistId()).isEqualTo(leadInA.getId());
        assertThat(after.getReviewedAt()).isNotNull();

        // Exactly one child exists in the church, with the expected fields
        List<Child> children = childRepository.findAll();
        assertThat(children).hasSize(1);
        Child child = children.get(0);
        assertThat(child.getStatus()).isEqualTo(ChildStatus.ACTIVE);
        assertThat(child.getKlassId()).isEqualTo(klassInA.getId());
        assertThat(child.getPendingRegistrationId()).isEqualTo(pending.getId());

        // Safety info exists, paired with the child via shared PK
        ChildSafetyInfo safetyInfo = childSafetyInfoRepository
                .findById(child.getId()).orElseThrow();
        assertThat(safetyInfo.getAllergies()).isEqualTo("Amendoim, glúten");
        assertThat(safetyInfo.getEmergencyContacts()).hasSize(1);
        assertThat(safetyInfo.getEmergencyContacts().get(0).name())
                .isEqualTo("Maria Silva");
    }

    @Test
    void approveAlreadyApprovedReturns409() throws Exception {
        PendingRegistration pending = persistPending(
                klassInA.getChurchId(), klassInA.getId(),
                "Gael", "Silva", null, List.of(
                        new EmergencyContact("Maria", "11999991234")));

        // First approval succeeds
        mockMvc.perform(post("/pending-registrations/{id}/approve", pending.getId())
                        .header("X-Dev-Catechist-Id", leadInA.getId().toString()))
                .andExpect(status().isOk());

        // Second approval is a conflict
        mockMvc.perform(post("/pending-registrations/{id}/approve", pending.getId())
                        .header("X-Dev-Catechist-Id", leadInA.getId().toString()))
                .andExpect(status().isConflict());

        // Exactly one child was created (no double-promotion)
        assertThat(childRepository.findAll()).hasSize(1);
    }

    @Test
    void approveAlreadyRejectedReturns409() throws Exception {
        PendingRegistration pending = persistPending(
                klassInA.getChurchId(), klassInA.getId(),
                "Gael", "Silva", null, List.of(
                        new EmergencyContact("Maria", "11999991234")));

        // Manually flip to REJECTED via the entity's lifecycle method
        pending.reject(leadInA.getId(), "Test rejection");
        pendingRegistrationRepository.save(pending);

        mockMvc.perform(post("/pending-registrations/{id}/approve", pending.getId())
                        .header("X-Dev-Catechist-Id", leadInA.getId().toString()))
                .andExpect(status().isConflict());

        // No child was created
        assertThat(childRepository.findAll()).isEmpty();
    }

    @Test
    void crossTenantLeadCannotApprove() throws Exception {
        PendingRegistration pending = persistPending(
                klassInA.getChurchId(), klassInA.getId(),
                "Gael", "Silva", null, List.of(
                        new EmergencyContact("Maria", "11999991234")));

        // Lead from church B targets a registration in church A
        mockMvc.perform(post("/pending-registrations/{id}/approve", pending.getId())
                        .header("X-Dev-Catechist-Id", leadInB.getId().toString()))
                .andExpect(status().isNotFound());

        // No state changes
        assertThat(childRepository.findAll()).isEmpty();
        PendingRegistration after = pendingRegistrationRepository
                .findById(pending.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(RegistrationStatus.PENDING);
    }

    @Test
    void plainCatechistIsForbiddenFromApproving() throws Exception {
        PendingRegistration pending = persistPending(
                klassInA.getChurchId(), klassInA.getId(),
                "Gael", "Silva", null, List.of(
                        new EmergencyContact("Maria", "11999991234")));

        mockMvc.perform(post("/pending-registrations/{id}/approve", pending.getId())
                        .header("X-Dev-Catechist-Id", plainCatechistInA.getId().toString()))
                .andExpect(status().isForbidden());

        // No state changes
        assertThat(childRepository.findAll()).isEmpty();
    }

    // ----- helpers -----

    private PendingRegistration persistPending(
            UUID churchId, UUID klassId,
            String firstName, String lastName,
            String allergies, List<EmergencyContact> contacts) {
        return pendingRegistrationRepository.save(new PendingRegistration(
                UUID.randomUUID(),
                churchId,
                klassId,
                firstName,
                lastName,
                allergies,
                contacts,
                "parent@example.com",
                "v1.0.0",
                Instant.now()));
    }
}