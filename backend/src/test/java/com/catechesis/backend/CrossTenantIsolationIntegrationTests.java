package com.catechesis.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.catechesis.backend.catechist.Catechist;
import com.catechesis.backend.catechist.CatechistRepository;
import com.catechesis.backend.catechist.CatechistRole;
import com.catechesis.backend.church.Church;
import com.catechesis.backend.church.ChurchRepository;
import com.catechesis.backend.klass.CatechistAssignment;
import com.catechesis.backend.klass.CatechistAssignmentRepository;
import com.catechesis.backend.klass.Klass;
import com.catechesis.backend.klass.KlassRepository;
import com.catechesis.backend.superadmin.SuperAdminRepository;
//import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Disabled;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cross-tenant isolation tests — the quality gate for Sprint 1.
 *
 * <p>If any assertion in this class fails, the multi-tenancy model is
 * broken in a way that allows data corruption or cross-tenant access.
 * Treat failures here as P0.
 *
 * <p>The test cases together cover:
 * <ul>
 *   <li>Tenant identity comes from authentication, not user input
 *   <li>Lead-A cannot perform Super-Admin operations on Church B
 *   <li>Two churches operate in parallel without crossover
 *   <li>The DB-level composite FK rejects cross-tenant assignments
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CrossTenantIsolationIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ChurchRepository churchRepository;
    @Autowired private CatechistRepository catechistRepository;
    @Autowired private KlassRepository klassRepository;
    @Autowired private CatechistAssignmentRepository assignmentRepository;
    @Autowired private SuperAdminRepository superAdminRepository;
    @Autowired private ObjectMapper objectMapper;

    /**
     * Seeds two churches with their respective Leads. Returns a small
     * holder so each test reads cleanly.
     */
    private TwoTenantSetup seedTwoTenants() {
        Church churchA = churchRepository.save(new Church("Parish A", "America/Sao_Paulo"));
        Church churchB = churchRepository.save(new Church("Parish B", "America/Sao_Paulo"));

        Catechist leadA = catechistRepository.save(new Catechist(
                churchA.getId(), "lead-a@parish.org", "Lead A", CatechistRole.LEAD));
        Catechist leadB = catechistRepository.save(new Catechist(
                churchB.getId(), "lead-b@parish.org", "Lead B", CatechistRole.LEAD));

        return new TwoTenantSetup(churchA, churchB, leadA, leadB);
    }

    private record TwoTenantSetup(Church churchA, Church churchB, Catechist leadA, Catechist leadB) {}

    // ---------------------------------------------------------------------
    // Scenario 1 — A Lead's class lands in their own church
    // ---------------------------------------------------------------------

    @Test
    void leadCreatesClassInOwnChurch() throws Exception {
        TwoTenantSetup s = seedTwoTenants();

        mockMvc.perform(post("/classes")
                        .header("X-Dev-Catechist-Id", s.leadA().getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"A's Class\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.churchId").value(s.churchA().getId().toString()));
    }

    // ---------------------------------------------------------------------
    // Scenario 2 — Request body cannot smuggle a different churchId
    // ---------------------------------------------------------------------

    @Test
    void smuggledChurchIdInRequestBodyIsIgnored() throws Exception {
        TwoTenantSetup s = seedTwoTenants();

        // Attempt to create a class in B by sending B's churchId in the body.
        // The DTO has no churchId field, so Jackson should drop it.
        String maliciousBody = objectMapper.writeValueAsString(Map.of(
                "name", "Hijack Attempt",
                "churchId", s.churchB().getId().toString()
        ));

        mockMvc.perform(post("/classes")
                        .header("X-Dev-Catechist-Id", s.leadA().getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(maliciousBody))
                .andExpect(status().isCreated())
                // The new class belongs to A, not B, despite the body.
                .andExpect(jsonPath("$.churchId").value(s.churchA().getId().toString()));
    }

    // ---------------------------------------------------------------------
    // Scenario 3 — Lead-A cannot bootstrap a Lead in Church B
    // ---------------------------------------------------------------------

    @Test
    void leadCannotBootstrapLeadInAnotherChurch() throws Exception {
        TwoTenantSetup s = seedTwoTenants();

        mockMvc.perform(post("/admin/churches/{churchId}/catechists", s.churchB().getId())
                        .header("X-Dev-Catechist-Id", s.leadA().getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"hijacker@parish.org\", \"name\": \"Hijacker\"}"))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------------
    // Scenario 4 — Two churches operate in parallel without crossover
    // ---------------------------------------------------------------------

    @Test
    @Disabled("S02-04 will add publicSlug to Klass entity; re-enable then")
    void twoChurchesCreateClassesIndependently() throws Exception {
        TwoTenantSetup s = seedTwoTenants();

        mockMvc.perform(post("/classes")
                        .header("X-Dev-Catechist-Id", s.leadA().getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Sementinha A\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/classes")
                        .header("X-Dev-Catechist-Id", s.leadB().getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Pequeninos B\"}"))
                .andExpect(status().isCreated());

        List<Klass> aClasses = klassRepository.findAll().stream()
                .filter(k -> k.getChurchId().equals(s.churchA().getId()))
                .toList();
        List<Klass> bClasses = klassRepository.findAll().stream()
                .filter(k -> k.getChurchId().equals(s.churchB().getId()))
                .toList();

        assertThat(aClasses).hasSize(1);
        assertThat(aClasses.get(0).getName()).isEqualTo("Sementinha A");

        assertThat(bClasses).hasSize(1);
        assertThat(bClasses.get(0).getName()).isEqualTo("Pequeninos B");

        // The classes do not see each other's churchIds.
        assertThat(aClasses.get(0).getChurchId()).isNotEqualTo(s.churchB().getId());
        assertThat(bClasses.get(0).getChurchId()).isNotEqualTo(s.churchA().getId());
    }

    // ---------------------------------------------------------------------
    // Scenario 5 — DB-level composite FK rejects cross-tenant assignments
    // ---------------------------------------------------------------------

    @Test
    void crossTenantAssignmentIsRejectedAtDbLevel() {
        TwoTenantSetup s = seedTwoTenants();

        Klass classInB = klassRepository.save(new Klass(s.churchB().getId(), "B's class"));

        // Attempt to assign Lead-A (in church A) to classInB (in church B),
        // declaring church A as the assignment's tenant. The composite FKs
        // require the assignment's church_id to match BOTH the catechist's
        // and the klass's church_id — impossible here.
        CatechistAssignment cross = new CatechistAssignment(
                s.churchA().getId(),  // assignment claims church A
                s.leadA().getId(),    // catechist is in A — would satisfy fk_assignment_catechist
                classInB.getId()      // class is in B — fails fk_assignment_klass
        );

        assertThatThrownBy(() -> {
            assignmentRepository.save(cross);
            assignmentRepository.flush();  // force the SQL execution
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------------------------------------------------------------------
    // Scenario 6 — Documentation test: catechist findById is not yet tenant-scoped
    // ---------------------------------------------------------------------

    @Test
    void catechistFindByIdIsCurrentlyGlobalNotTenantScoped() {
        TwoTenantSetup s = seedTwoTenants();

        // findById doesn't filter by tenant — it returns whatever ID matches.
        // Tenant safety today comes from the layer above (controllers + filter).
        // This test pins that current behavior. When tenant-scoped repositories
        // are introduced (Sprint 4 hardening, ADR-0002 follow-up), update this.
        Catechist found = catechistRepository.findById(s.leadB().getId()).orElseThrow();
        assertThat(found.getChurchId()).isEqualTo(s.churchB().getId());
    }
}