package com.catechesis.backend.roster.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.catechesis.backend.church.Church;
import com.catechesis.backend.church.ChurchRepository;
import com.catechesis.backend.common.slug.SlugGenerator;
import com.catechesis.backend.klass.Klass;
import com.catechesis.backend.klass.KlassRepository;
import java.util.List;
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
 * Integration tests for the public registration endpoint
 * ({@code POST /public/classes/{slug}/registrations}).
 *
 * <p>Covers happy path, validation failures, slug resolution failures
 * (including the inactive-klass enumeration-resistance property), and
 * consent enforcement. The S02-13 quality gate adds deeper public-surface
 * security tests on top of this baseline.
 *
 * <p>Sets the rate-limit property high enough that the eight test
 * methods don't trip the limiter. The rate limiter's behavior is
 * exhaustively covered by its own unit and integration tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "app.rate-limit.public-registration-per-hour=1000"
})
class PublicRegistrationIntegrationTests {

    private static final SlugGenerator SLUG_GENERATOR = new SlugGenerator();

    @Autowired MockMvc mockMvc;
    @Autowired ChurchRepository churchRepository;
    @Autowired KlassRepository klassRepository;
    @Autowired PendingRegistrationRepository pendingRegistrationRepository;

    private Church church;
    private Klass activeKlass;
    private Klass inactiveKlass;

    @BeforeEach
    void setUp() {
        church = churchRepository.save(new Church("Test Parish", "America/Sao_Paulo"));

        activeKlass = klassRepository.save(new Klass(
                UUID.randomUUID(), church.getId(),
                "Active Class", SLUG_GENERATOR.generate()));

        Klass inactive = new Klass(
                UUID.randomUUID(), church.getId(),
                "Inactive Class", SLUG_GENERATOR.generate());
        inactive.setActive(false);
        inactiveKlass = klassRepository.save(inactive);
    }

    // ----- Happy path -----

    @Test
    void validSubmissionCreatesPendingRegistration() throws Exception {
        String body = validRequestBody();

        mockMvc.perform(post("/public/classes/{slug}/registrations",
                        activeKlass.getPublicSlug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        List<PendingRegistration> all = pendingRegistrationRepository.findAll();
        assertThat(all).hasSize(1);

        PendingRegistration created = all.get(0);
        assertThat(created.getStatus()).isEqualTo(RegistrationStatus.PENDING);
        assertThat(created.getChurchId()).isEqualTo(church.getId());
        assertThat(created.getKlassId()).isEqualTo(activeKlass.getId());
        assertThat(created.getChildFirstName()).isEqualTo("Gael");
        assertThat(created.getChildLastName()).isEqualTo("Silva");
        assertThat(created.getEmergencyContacts()).hasSize(1);
        assertThat(created.getConsentVersion()).isEqualTo("v1.0.0");
        assertThat(created.getConsentGrantedAt()).isNotNull();
        assertThat(created.getSubmittedAt()).isNotNull();
    }

    // ----- Slug resolution failures -----

    @Test
    void unknownSlugReturns404() throws Exception {
        mockMvc.perform(post("/public/classes/{slug}/registrations", "nosuchslug")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isNotFound());

        assertThat(pendingRegistrationRepository.findAll()).isEmpty();
    }

    @Test
    void inactiveKlassReturns404SameAsUnknown() throws Exception {
        mockMvc.perform(post("/public/classes/{slug}/registrations",
                        inactiveKlass.getPublicSlug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isNotFound());

        assertThat(pendingRegistrationRepository.findAll()).isEmpty();
    }

    // ----- Validation failures -----

    @Test
    void missingChildFirstNameReturns400() throws Exception {
        String body = """
                {
                  "childFirstName": "",
                  "childLastName": "Silva",
                  "allergies": null,
                  "emergencyContacts": [{"name": "Maria", "phone": "11999991234"}],
                  "parentContactEmail": "maria@example.com",
                  "consentAccepted": true
                }
                """;

        mockMvc.perform(post("/public/classes/{slug}/registrations",
                        activeKlass.getPublicSlug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidEmailReturns400() throws Exception {
        String body = """
                {
                  "childFirstName": "Gael",
                  "childLastName": "Silva",
                  "allergies": null,
                  "emergencyContacts": [{"name": "Maria", "phone": "11999991234"}],
                  "parentContactEmail": "not-an-email",
                  "consentAccepted": true
                }
                """;

        mockMvc.perform(post("/public/classes/{slug}/registrations",
                        activeKlass.getPublicSlug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void emptyEmergencyContactsReturns400() throws Exception {
        String body = """
                {
                  "childFirstName": "Gael",
                  "childLastName": "Silva",
                  "allergies": null,
                  "emergencyContacts": [],
                  "parentContactEmail": "maria@example.com",
                  "consentAccepted": true
                }
                """;

        mockMvc.perform(post("/public/classes/{slug}/registrations",
                        activeKlass.getPublicSlug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void emergencyContactWithBlankNameReturns400() throws Exception {
        // Tests that @Valid recurses into the list elements
        String body = """
                {
                  "childFirstName": "Gael",
                  "childLastName": "Silva",
                  "allergies": null,
                  "emergencyContacts": [{"name": "", "phone": "11999991234"}],
                  "parentContactEmail": "maria@example.com",
                  "consentAccepted": true
                }
                """;

        mockMvc.perform(post("/public/classes/{slug}/registrations",
                        activeKlass.getPublicSlug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void consentNotAcceptedReturns400() throws Exception {
        String body = """
                {
                  "childFirstName": "Gael",
                  "childLastName": "Silva",
                  "allergies": null,
                  "emergencyContacts": [{"name": "Maria", "phone": "11999991234"}],
                  "parentContactEmail": "maria@example.com",
                  "consentAccepted": false
                }
                """;

        mockMvc.perform(post("/public/classes/{slug}/registrations",
                        activeKlass.getPublicSlug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ----- Helpers -----

    private static String validRequestBody() {
        return """
                {
                  "childFirstName": "Gael",
                  "childLastName": "Silva",
                  "allergies": "Amendoim, glúten",
                  "emergencyContacts": [
                    {"name": "Maria Silva", "phone": "+55 11 99999-1234"}
                  ],
                  "parentContactEmail": "maria.silva@example.com",
                  "consentAccepted": true
                }
                """;
    }
}