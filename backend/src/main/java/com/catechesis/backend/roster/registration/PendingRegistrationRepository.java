package com.catechesis.backend.roster.registration;

import com.catechesis.backend.roster.registration.dto.PendingRegistrationSummary;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository for {@link PendingRegistration}.
 *
 * <p>Adds a JPQL projection query for the Lead-side list endpoint
 * (S02-09). The projection bypasses entity hydration: Hibernate
 * constructs each {@link PendingRegistrationSummary} directly from
 * the row's projected columns, so Tier-3 fields are never read into
 * memory on this code path.
 */
public interface PendingRegistrationRepository
        extends JpaRepository<PendingRegistration, UUID> {

    @Query("""
            SELECT new com.catechesis.backend.roster.registration.dto.PendingRegistrationSummary(
                pr.id,
                pr.klassId,
                k.name,
                pr.childFirstName,
                pr.childLastName,
                pr.parentContactEmail,
                pr.submittedAt
            )
            FROM PendingRegistration pr
            JOIN Klass k ON pr.klassId = k.id
            WHERE pr.churchId = :churchId
              AND pr.status = :status
            ORDER BY pr.submittedAt DESC
            """)
    List<PendingRegistrationSummary> findSummariesByChurchAndStatus(
            @Param("churchId") UUID churchId,
            @Param("status") RegistrationStatus status);
}