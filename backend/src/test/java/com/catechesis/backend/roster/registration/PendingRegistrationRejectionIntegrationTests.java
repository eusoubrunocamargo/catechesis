package com.catechesis.backend.roster.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.catechesis.backend.catechist.Catechist;
import com.catechesis.backend.catechist.CatechistRepository;
import com.catechesis.backend.catechist.CatechistRole;
import com.catechesis.backend.church.Church;
import com.catechesis.backend.church.ChurchRepository;
import com.catechesis.backend.common.slug.SlugGenerator;
import com.catechesis.backend.klass.Klass;
import com.catechesis.backend.klass.KlassRepository;
import com.catechesis.backend.roster.child.ChildRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for {@code POST /pending-registrations/{id}/reject}.
 *
 * <p>Covers the happy paths (with and without reason), conflict cases
 * (already-approved, already-rejected), tenant isolation, and
 * authorization. No Child or ChildSafetyInfo is ever created on the
 * rejection path — verified via {@code childRepository.findAll()}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PendingRegistrationRejectionIntegrationTests {

    private static final SlugGenerator SLUG_GENERATOR = new SlugGenerator();

    @Autowired MockMvc mockMvc;
    @Autowired ChurchRepository churchRepository;
    @Autowired CatechistRepository catechistRepository;
    @Autowired KlassRepository klassRepository;
    @Autowired PendingRegistrationRepository pendingRegistrationRepository;
    @Autowired ChildRepository childRepository;

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
    void leadRejectsWithReason() throws Exception {
        PendingRegistration pending = persistPending(klassInA);

        mockMvc.perform(post("/pending-registrations/{id}/reject", pending.getId())
                        .header("X-Dev-Catechist-Id", leadInA.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason": "Duplicate submission"}
                                """))
                .andExpect(status().isOk());

        PendingRegistration after = pendingRegistrationRepository
                .findById(pending.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(RegistrationStatus.REJECTED);
        assertThat(after.getReviewedByCatechistId()).isEqualTo(leadInA.getId());
        assertThat(after.getReviewedAt()).isNotNull();
        assertThat(after.getRejectionReason()).isEqualTo("Duplicate submission");

        // No child should have been created on the rejection path
        assertThat(childRepository.findAll()).isEmpty();
    }

    @Test
    void leadRejectsWithoutReason() throws Exception {
        PendingRegistration pending = persistPending(klassInA);

        mockMvc.perform(post("/pending-registrations/{id}/reject", pending.getId())
                        .header("X-Dev-Catechist-Id", leadInA.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        PendingRegistration after = pendingRegistrationRepository
                .findById(pending.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(RegistrationStatus.REJECTED);
        assertThat(after.getRejectionReason()).isNull();
    }

    @Test
    void rejectAlreadyRejectedReturns409() throws Exception {
        PendingRegistration pending = persistPending(klassInA);

        // First rejection succeeds
        mockMvc.perform(post("/pending-registrations/{id}/reject", pending.getId())
                        .header("X-Dev-Catechist-Id", leadInA.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        // Second rejection is a conflict
        mockMvc.perform(post("/pending-registrations/{id}/reject", pending.getId())
                        .header("X-Dev-Catechist-Id", leadInA.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectAlreadyApprovedReturns409() throws Exception {
        PendingRegistration pending = persistPending(klassInA);

        // Approve first via the entity's lifecycle method
        pending.approve(leadInA.getId());
        pendingRegistrationRepository.save(pending);

        mockMvc.perform(post("/pending-registrations/{id}/reject", pending.getId())
                        .header("X-Dev-Catechist-Id", leadInA.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());

        PendingRegistration after = pendingRegistrationRepository
                .findById(pending.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(RegistrationStatus.APPROVED);
    }

    @Test
    void crossTenantLeadCannotReject() throws Exception {
        PendingRegistration pending = persistPending(klassInA);

        mockMvc.perform(post("/pending-registrations/{id}/reject", pending.getId())
                        .header("X-Dev-Catechist-Id", leadInB.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());

        PendingRegistration after = pendingRegistrationRepository
                .findById(pending.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(RegistrationStatus.PENDING);
    }

    @Test
    void plainCatechistIsForbiddenFromRejecting() throws Exception {
        PendingRegistration pending = persistPending(klassInA);

        mockMvc.perform(post("/pending-registrations/{id}/reject", pending.getId())
                        .header("X-Dev-Catechist-Id", plainCatechistInA.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    // ----- helpers -----

    private PendingRegistration persistPending(Klass klass) {
        return pendingRegistrationRepository.save(new PendingRegistration(
                UUID.randomUUID(),
                klass.getChurchId(),
                klass.getId(),
                "Gael",
                "Silva",
                null,
                List.of(new EmergencyContact("Maria", "11999991234")),
                "parent@example.com",
                "v1.0.0",
                Instant.now()));
    }
}