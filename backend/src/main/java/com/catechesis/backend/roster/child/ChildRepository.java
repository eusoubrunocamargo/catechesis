package com.catechesis.backend.roster.child;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link Child}.
 *
 * <p>Empty by design for S02-04. Roster query methods (e.g.
 * {@code findByKlassIdAndStatus}) are added in later sprints when
 * roster-listing endpoints need them. {@code save} and {@code findById}
 * from {@link JpaRepository} are sufficient for the smoke test in this
 * item.
 */
public interface ChildRepository extends JpaRepository<Child, UUID> { }