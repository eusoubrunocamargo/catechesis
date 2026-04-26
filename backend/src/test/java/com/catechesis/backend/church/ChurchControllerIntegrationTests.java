package com.catechesis.backend.church;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.catechesis.backend.catechist.Catechist;
import com.catechesis.backend.catechist.CatechistRepository;
import com.catechesis.backend.catechist.CatechistRole;
import com.catechesis.backend.superadmin.SuperAdmin;
import com.catechesis.backend.superadmin.SuperAdminRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ChurchControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SuperAdminRepository superAdminRepository;

    @Autowired
    private CatechistRepository catechistRepository;

    private static final String CREATE_CHURCH_BODY = """
        {
          "displayName": "Paróquia de Teste",
          "timezone": "America/Sao_Paulo"
        }
        """;

    @Test
    void superAdminCreatesChurchSuccessfully() throws Exception {
        SuperAdmin admin = superAdminRepository.save(
                new SuperAdmin("super@test.com", "Test Super-Admin")
        );

        mockMvc.perform(post("/admin/churches")
                        .header("X-Dev-Super-Admin-Id", admin.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_CHURCH_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.displayName").value("Paróquia de Teste"))
                .andExpect(jsonPath("$.timezone").value("America/Sao_Paulo"))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void catechistCannotCreateChurch() throws Exception {
        // To create a Catechist we need a Church first — bootstrap one
        // directly via the repository.
        com.catechesis.backend.church.Church seed = new com.catechesis.backend.church.Church(
                "Seed Parish", "America/Sao_Paulo");
        // Save via repository in a quick way:
        // (Pulling in ChurchRepository for the test would be cleaner;
        //  for brevity we use the JPA EntityManager via a small autowire below.)
        seed = churchRepository.save(seed);

        Catechist catechist = catechistRepository.save(
                new Catechist(seed.getId(), "lead@test.com", "Test Lead", CatechistRole.LEAD)
        );

        mockMvc.perform(post("/admin/churches")
                        .header("X-Dev-Catechist-Id", catechist.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_CHURCH_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCannotCreateChurch() throws Exception {
        mockMvc.perform(post("/admin/churches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_CHURCH_BODY))
                .andExpect(status().isForbidden());
    }

    @Autowired
    private ChurchRepository churchRepository;
}