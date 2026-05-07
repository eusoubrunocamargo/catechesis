package com.catechesis.backend.roster.registration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for {@code GET /pending-registrations}.
 *
 * <p>Covers the happy path (Lead sees their church's PENDING
 * registrations only), tenant isolation (Lead-A does not see Lead-B's
 * pending registrations), and authorization (a non-Lead is forbidden).
 *
 * <p>Specifically asserts that Tier-3 fields (allergies, emergency
 * contacts) are absent from the list response — the
 * privacy-by-default property from ADR-0003.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PendingRegistrationListIntegrationTests {

    private static final SlugGenerator SLUG_GENERATOR = new SlugGenerator();

    @Autowired MockMvc mockMvc;
    @Autowired ChurchRepository churchRepository;
    @Autowired CatechistRepository catechistRepository;
    @Autowired KlassRepository klassRepository;
    @Autowired PendingRegistrationRepository pendingRegistrationRepository;

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
        Klass klassInB = klassRepository.save(new Klass(
                UUID.randomUUID(), churchB.getId(),
                "Pequeninos B", SLUG_GENERATOR.generate()));

        // One PENDING registration in each church
        pendingRegistrationRepository.save(buildPending(
                churchA.getId(), klassInA.getId(), "Gael", "Silva"));
        pendingRegistrationRepository.save(buildPending(
                churchB.getId(), klassInB.getId(), "Beatriz", "Souza"));
    }

    @Test
    void leadSeesOnlyOwnChurchPendingRegistrations() throws Exception {
        mockMvc.perform(get("/pending-registrations")
                        .header("X-Dev-Catechist-Id", leadInA.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].childFirstName").value("Gael"))
                .andExpect(jsonPath("$[0].klassName").value("Sementinha A"))
                .andExpect(jsonPath("$[0].parentContactEmail")
                        .value("parent@example.com"))
                // Tier-3 fields must not appear in the list response
                .andExpect(jsonPath("$[0].allergies").doesNotExist())
                .andExpect(jsonPath("$[0].emergencyContacts").doesNotExist());
    }

    @Test
    void crossTenantLeadDoesNotSeeOthersPendingRegistrations() throws Exception {
        mockMvc.perform(get("/pending-registrations")
                        .header("X-Dev-Catechist-Id", leadInB.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].childFirstName").value("Beatriz"));
    }

    @Test
    void plainCatechistIsForbidden() throws Exception {
        mockMvc.perform(get("/pending-registrations")
                        .header("X-Dev-Catechist-Id", plainCatechistInA.getId().toString()))
                .andExpect(status().isForbidden());
    }

    // ----- helpers -----

    private PendingRegistration buildPending(
            UUID churchId, UUID klassId, String firstName, String lastName) {
        return new PendingRegistration(
                UUID.randomUUID(),
                churchId,
                klassId,
                firstName,
                lastName,
                null,
                List.of(new EmergencyContact("Parent", "11999991234")),
                "parent@example.com",
                "v1.0.0",
                Instant.now());
    }
}