package com.catechesis.backend.klass;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.catechesis.backend.catechist.Catechist;
import com.catechesis.backend.catechist.CatechistRepository;
import com.catechesis.backend.catechist.CatechistRole;
import com.catechesis.backend.church.Church;
import com.catechesis.backend.church.ChurchRepository;
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
class KlassControllerIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ChurchRepository churchRepository;
    @Autowired private CatechistRepository catechistRepository;
    @Autowired private SuperAdminRepository superAdminRepository;

    private static final String CREATE_KLASS_BODY = """
        {
          "name": "Sementinha III A"
        }
        """;

    @Test
    void leadCreatesClassInTheirChurch() throws Exception {
        Church church = churchRepository.save(new Church("Test Parish", "America/Sao_Paulo"));
        Catechist lead = catechistRepository.save(new Catechist(
                church.getId(), "lead@parish.org", "Test Lead", CatechistRole.LEAD));

        mockMvc.perform(post("/classes")
                        .header("X-Dev-Catechist-Id", lead.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_KLASS_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.churchId").value(church.getId().toString()))
                .andExpect(jsonPath("$.name").value("Sementinha III A"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void regularCatechistCannotCreateClass() throws Exception {
        Church church = churchRepository.save(new Church("Test Parish", "America/Sao_Paulo"));
        Catechist plain = catechistRepository.save(new Catechist(
                church.getId(), "plain@parish.org", "Plain Catechist", CatechistRole.CATECHIST));

        mockMvc.perform(post("/classes")
                        .header("X-Dev-Catechist-Id", plain.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_KLASS_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void superAdminCannotCreateClass() throws Exception {
        SuperAdmin admin = superAdminRepository.save(new SuperAdmin("admin@test.com", "Admin"));

        mockMvc.perform(post("/classes")
                        .header("X-Dev-Super-Admin-Id", admin.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_KLASS_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCannotCreateClass() throws Exception {
        mockMvc.perform(post("/classes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_KLASS_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void blankNameReturns400() throws Exception {
        Church church = churchRepository.save(new Church("Test Parish", "America/Sao_Paulo"));
        Catechist lead = catechistRepository.save(new Catechist(
                church.getId(), "lead@parish.org", "Test Lead", CatechistRole.LEAD));

        mockMvc.perform(post("/classes")
                        .header("X-Dev-Catechist-Id", lead.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"  \"}"))
                .andExpect(status().isBadRequest());
    }
}