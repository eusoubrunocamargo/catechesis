package com.catechesis.backend.catechist;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.catechesis.backend.church.Church;
import com.catechesis.backend.church.ChurchRepository;
import com.catechesis.backend.superadmin.SuperAdmin;
import com.catechesis.backend.superadmin.SuperAdminRepository;
import java.util.UUID;
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
class CatechistControllerIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private SuperAdminRepository superAdminRepository;
    @Autowired private ChurchRepository churchRepository;
    @Autowired private CatechistRepository catechistRepository;

    private static final String CREATE_LEAD_BODY = """
        {
          "email": "marina@parish.org",
          "name": "Marina"
        }
        """;

    @Test
    void superAdminCreatesFirstLead() throws Exception {
        SuperAdmin admin = superAdminRepository.save(new SuperAdmin("admin@test.com", "Admin"));
        Church church = churchRepository.save(new Church("Test Parish", "America/Sao_Paulo"));

        mockMvc.perform(post("/admin/churches/{churchId}/catechists", church.getId())
                        .header("X-Dev-Super-Admin-Id", admin.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_LEAD_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.churchId").value(church.getId().toString()))
                .andExpect(jsonPath("$.email").value("marina@parish.org"))
                .andExpect(jsonPath("$.name").value("Marina"))
                .andExpect(jsonPath("$.role").value("LEAD"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void duplicateLeadAttemptReturns409() throws Exception {
        SuperAdmin admin = superAdminRepository.save(new SuperAdmin("admin@test.com", "Admin"));
        Church church = churchRepository.save(new Church("Test Parish", "America/Sao_Paulo"));

        // First Lead — succeeds
        catechistRepository.save(new Catechist(
                church.getId(), "first@parish.org", "First Lead", CatechistRole.LEAD));

        // Second attempt via the endpoint — should fail with 409
        mockMvc.perform(post("/admin/churches/{churchId}/catechists", church.getId())
                        .header("X-Dev-Super-Admin-Id", admin.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_LEAD_BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void unknownChurchReturns404() throws Exception {
        SuperAdmin admin = superAdminRepository.save(new SuperAdmin("admin@test.com", "Admin"));
        UUID nonexistent = UUID.fromString("99999999-9999-9999-9999-999999999999");

        mockMvc.perform(post("/admin/churches/{churchId}/catechists", nonexistent)
                        .header("X-Dev-Super-Admin-Id", admin.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_LEAD_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void catechistCannotCreateLead() throws Exception {
        Church church = churchRepository.save(new Church("Test Parish", "America/Sao_Paulo"));
        Catechist existingLead = catechistRepository.save(new Catechist(
                church.getId(), "lead@parish.org", "Existing Lead", CatechistRole.LEAD));

        mockMvc.perform(post("/admin/churches/{churchId}/catechists", church.getId())
                        .header("X-Dev-Catechist-Id", existingLead.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_LEAD_BODY))
                .andExpect(status().isForbidden());
    }
}