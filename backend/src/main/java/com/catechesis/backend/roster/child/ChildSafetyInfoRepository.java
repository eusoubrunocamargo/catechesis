package com.catechesis.backend.roster.child;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link ChildSafetyInfo}.
 *
 * <p>The primary key type is {@code UUID} — specifically, the
 * {@code childId} of the paired child. {@code save} and {@code findById}
 * from {@link JpaRepository} are sufficient for Sprint 2.
 *
 * <p>Future redaction-related queries (e.g.
 * {@code findBySensitiveDataRedactedAtIsNull}) may be added in
 * later sprints when admin tooling needs them.
 */
public interface ChildSafetyInfoRepository extends JpaRepository<ChildSafetyInfo, UUID> { }