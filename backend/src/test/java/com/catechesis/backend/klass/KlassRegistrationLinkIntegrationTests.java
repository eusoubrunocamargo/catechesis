package com.catechesis.backend.klass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.catechesis.backend.catechist.Catechist;
import com.catechesis.backend.catechist.CatechistRepository;
import com.catechesis.backend.catechist.CatechistRole;
import com.catechesis.backend.church.Church;
import com.catechesis.backend.church.ChurchRepository;
import com.catechesis.backend.common.slug.SlugGenerator;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for {@code GET /classes/{id}/registration-link}.
 *
 * <p>Covers happy path (Lead retrieves their klass's link), idempotency
 * (two calls return the same slug), authorization (non-Lead is forbidden),
 * and tenant isolation (Lead from a different church gets 404, not 403).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class KlassRegistrationLinkIntegrationTests {

    private static final SlugGenerator SLUG_GENERATOR = new SlugGenerator();

    @Autowired MockMvc mockMvc;
    @Autowired ChurchRepository churchRepository;
    @Autowired CatechistRepository catechistRepository;
    @Autowired KlassRepository klassRepository;

    private Church churchA;
    private Catechist leadInA;
    private Catechist plainCatechistInA;
    private Catechist leadInB;
    private Klass klassInA;

    @BeforeEach
    void setUp() {
        churchA = churchRepository.save(new Church("Parish A", "America/Sao_Paulo"));
        Church churchB = churchRepository.save(new Church("Parish B", "America/Sao_Paulo"));

        leadInA = catechistRepository.save(buildCatechist(
                churchA.getId(), "lead-a@example.com", "Lead A", CatechistRole.LEAD));
        plainCatechistInA = catechistRepository.save(buildCatechist(
                churchA.getId(), "catechist-a@example.com", "Catechist A", CatechistRole.CATECHIST));
        leadInB = catechistRepository.save(buildCatechist(
                churchB.getId(), "lead-b@example.com", "Lead B", CatechistRole.LEAD));

        klassInA = klassRepository.save(new Klass(
                UUID.randomUUID(), churchA.getId(), "Sementinha A", SLUG_GENERATOR.generate()));
    }

    @Test
    void leadRetrievesRegistrationLinkForOwnKlass() throws Exception {
        mockMvc.perform(get("/classes/{id}/registration-link", klassInA.getId())
                        .header("X-Dev-Catechist-Id", leadInA.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value(klassInA.getPublicSlug()))
                .andExpect(jsonPath("$.publicUrl")
                        .value("http://localhost:5173/r/" + klassInA.getPublicSlug()));
    }

    @Test
    void twoCallsReturnTheSameSlug() throws Exception {
        String firstSlug = extractSlug(callLink(leadInA, klassInA));
        String secondSlug = extractSlug(callLink(leadInA, klassInA));

        assertThat(firstSlug).isEqualTo(secondSlug);
        assertThat(firstSlug).isEqualTo(klassInA.getPublicSlug());
    }

    @Test
    void plainCatechistIsForbidden() throws Exception {
        mockMvc.perform(get("/classes/{id}/registration-link", klassInA.getId())
                        .header("X-Dev-Catechist-Id", plainCatechistInA.getId().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void leadFromAnotherChurchGetsNotFound() throws Exception {
        mockMvc.perform(get("/classes/{id}/registration-link", klassInA.getId())
                        .header("X-Dev-Catechist-Id", leadInB.getId().toString()))
                .andExpect(status().isNotFound());
    }

    // ----- helpers -----

    private Catechist buildCatechist(UUID churchId, String email,
                                     String name, CatechistRole role) {
        // Adjust constructor signature to match your Catechist entity.
        return new Catechist(churchId, email, name, role);
    }

    private String callLink(Catechist actor, Klass klass) throws Exception {
        return mockMvc.perform(get("/classes/{id}/registration-link", klass.getId())
                        .header("X-Dev-Catechist-Id", actor.getId().toString()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String extractSlug(String responseBody) {
        // Simple extraction without pulling in a full JSON library.
        // Format: {"slug":"XXXX","publicUrl":"..."}
        int start = responseBody.indexOf("\"slug\":\"") + "\"slug\":\"".length();
        int end = responseBody.indexOf("\"", start);
        return responseBody.substring(start, end);
    }
}