package com.catechesis.backend.roster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.catechesis.backend.catechist.Catechist;
import com.catechesis.backend.catechist.CatechistRepository;
import com.catechesis.backend.catechist.CatechistRole;
import com.catechesis.backend.church.Church;
import com.catechesis.backend.church.ChurchRepository;
import com.catechesis.backend.common.slug.SlugGenerator;
import com.catechesis.backend.klass.Klass;
import com.catechesis.backend.klass.KlassRepository;
import com.catechesis.backend.roster.child.ChildRepository;
import com.catechesis.backend.roster.child.ChildSafetyInfo;
import com.catechesis.backend.roster.child.ChildSafetyInfoRepository;
import com.catechesis.backend.roster.registration.EmergencyContact;
import com.catechesis.backend.roster.registration.PendingRegistration;
import com.catechesis.backend.roster.registration.PendingRegistrationRepository;
import com.catechesis.backend.roster.registration.RegistrationStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Quality-gate test for Sprint 2 property 6: approval atomicity.
 *
 * <p>The approval flow performs three writes (Child insert, ChildSafetyInfo
 * insert, PendingRegistration status update) within a single transaction.
 * If any write fails mid-flight, all three must roll back — no Child row,
 * no ChildSafetyInfo row, no APPROVED status on the registration.
 *
 * <p>This test forces a failure by replacing
 * {@link ChildSafetyInfoRepository} with a Mockito mock whose
 * {@code save} method throws. The exception originates mid-transaction;
 * {@code @Transactional} on the service method must ensure the entire
 * transaction rolls back to pre-call state.
 *
 * <p>NOT marked {@code @Transactional} at the class level — unlike most
 * tests in the suite. The test-class transaction would wrap the
 * service's transaction, masking the rollback we're trying to observe.
 * Cleanup happens in {@link #cleanUp()} instead.
 *
 * <p>Lives in its own test class because {@code @MockitoBean} replaces
 * the bean for the entire Spring context.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApprovalAtomicityIntegrationTests {


    private static final SlugGenerator SLUG_GENERATOR = new SlugGenerator();

    @Autowired MockMvc mockMvc;
    @Autowired ChurchRepository churchRepository;
    @Autowired CatechistRepository catechistRepository;
    @Autowired KlassRepository klassRepository;
    @Autowired PendingRegistrationRepository pendingRegistrationRepository;
    @Autowired ChildRepository childRepository;

    @MockitoBean ChildSafetyInfoRepository childSafetyInfoRepository;

    private Catechist lead;
    private Klass klass;
    private Church church;

    @BeforeEach
    void setUp() {
        church = churchRepository.save(
                new Church("Test Parish", "America/Sao_Paulo"));
        lead = catechistRepository.save(new Catechist(
                church.getId(), "lead@example.com", "Lead", CatechistRole.LEAD));
        klass = klassRepository.save(new Klass(
                UUID.randomUUID(), church.getId(),
                "Test Class", SLUG_GENERATOR.generate()));
    }

    @AfterEach
    void cleanUp() {
        // Without class-level @Transactional, we have to undo our setup
        // manually. Delete in reverse FK order: children → pending
        // registrations → klass → catechist → church.
        childRepository.deleteAll();
        pendingRegistrationRepository.deleteAll();
        klassRepository.deleteAll();
        catechistRepository.deleteAll();
        churchRepository.deleteAll();
    }

    @Test
    void approvalRollsBackEntireTransactionWhenSafetyInfoSaveFails() {
        // Set up a pending registration in the same church as the Lead
        PendingRegistration pending = pendingRegistrationRepository.save(
                buildPending(klass));
        UUID pendingId = pending.getId();

        // Force ChildSafetyInfo save to throw — simulates any failure
        // (DB error, constraint violation, connection drop) that could
        // occur mid-transaction
        when(childSafetyInfoRepository.save(any(ChildSafetyInfo.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "Simulated failure for atomicity test"));

        // The approval attempt must propagate the exception. MockMvc
        // does not translate unhandled exceptions into HTTP responses
        // (no @ControllerAdvice yet — that's Sprint 4). The exception
        // escapes mockMvc.perform() directly. What matters for the
        // atomicity property is the rollback's effect on persisted
        // state, asserted below.
        assertThatThrownBy(() ->
                mockMvc.perform(post("/pending-registrations/{id}/approve", pendingId)
                        .header("X-Dev-Catechist-Id", lead.getId().toString())))
                .hasRootCauseInstanceOf(DataIntegrityViolationException.class);

        // CRITICAL ASSERTIONS: the transaction rolled back genuinely
        // at the JDBC level (no class-level @Transactional masks this).
        //
        // The pending registration must NOT be in APPROVED status.
        PendingRegistration after = pendingRegistrationRepository
                .findById(pendingId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(RegistrationStatus.PENDING);
        assertThat(after.getReviewedByCatechistId()).isNull();
        assertThat(after.getReviewedAt()).isNull();

        // No Child row exists — the insert was rolled back along with
        // the status update
        assertThat(childRepository.findAll()).isEmpty();
    }

    private PendingRegistration buildPending(Klass klass) {
        return new PendingRegistration(
                UUID.randomUUID(),
                klass.getChurchId(),
                klass.getId(),
                "Gael",
                "Silva",
                "Amendoim",
                List.of(new EmergencyContact("Maria", "11999991234")),
                "maria@example.com",
                "v1.0.0",
                Instant.now());
    }
}