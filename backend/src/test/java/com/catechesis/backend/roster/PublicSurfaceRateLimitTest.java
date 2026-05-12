package com.catechesis.backend.roster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.catechesis.backend.church.Church;
import com.catechesis.backend.church.ChurchRepository;
import com.catechesis.backend.common.slug.SlugGenerator;
import com.catechesis.backend.klass.Klass;
import com.catechesis.backend.klass.KlassRepository;
import com.catechesis.backend.roster.registration.PendingRegistrationRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quality-gate test for Sprint 2 property 2: rate limiting holds.
 *
 * <p>Lives in its own test class because the rate-limiter's in-memory
 * bucket state is shared across all tests in a class — if the limit
 * is set low (as it must be to exercise it in a test), other tests
 * issuing requests to {@code /public/**} would prematurely exhaust the
 * bucket. Isolation keeps the rate-limit test self-contained.
 *
 * <p>See {@link PublicSurfaceSecurityIntegrationTests} for the
 * consolidated quality-gate suite covering the other six properties.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "app.rate-limit.public-registration-per-hour=3"
})
class PublicSurfaceRateLimitTest {

    private static final SlugGenerator SLUG_GENERATOR = new SlugGenerator();

    @Autowired MockMvc mockMvc;
    @Autowired ChurchRepository churchRepository;
    @Autowired KlassRepository klassRepository;
    @Autowired PendingRegistrationRepository pendingRegistrationRepository;

    private Klass activeKlass;

    @BeforeEach
    void setUp() {
        Church church = churchRepository.save(
                new Church("Test Parish", "America/Sao_Paulo"));
        activeKlass = klassRepository.save(new Klass(
                UUID.randomUUID(), church.getId(),
                "Active Class", SLUG_GENERATOR.generate()));
    }

    @Test
    void rateLimit_overLimitRequestReturns429() throws Exception {
        // Configured limit is 3 per hour. Burn the bucket with three
        // legitimate submissions, then expect the fourth to be rejected.

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/public/classes/{slug}/registrations",
                            activeKlass.getPublicSlug())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validRequestBody()))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(post("/public/classes/{slug}/registrations",
                        activeKlass.getPublicSlug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isTooManyRequests());

        // Exactly three registrations made it through — the fourth was
        // blocked before any business logic ran
        assertThat(pendingRegistrationRepository.findAll()).hasSize(3);
    }

    private static String validRequestBody() {
        return """
                {
                  "childFirstName": "Gael",
                  "childLastName": "Silva",
                  "allergies": "Amendoim",
                  "emergencyContacts": [
                    {"name": "Maria Silva", "phone": "+55 11 99999-1234"}
                  ],
                  "parentContactEmail": "maria.silva@example.com",
                  "consentAccepted": true
                }
                """;
    }
}