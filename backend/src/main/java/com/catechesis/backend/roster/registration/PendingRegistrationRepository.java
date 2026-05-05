package com.catechesis.backend.roster.registration;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link PendingRegistration}.
 *
 * <p>Empty by design for S02-04. Query methods (e.g.
 * {@code findByKlassIdAndStatus}) are added in S02-09 when the list
 * endpoint needs them. {@code save} and {@code findById} from
 * {@link JpaRepository} are sufficient for the smoke test in this item.
 */
public interface PendingRegistrationRepository
        extends JpaRepository<PendingRegistration, UUID> { }