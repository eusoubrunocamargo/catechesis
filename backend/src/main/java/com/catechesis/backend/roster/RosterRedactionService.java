package com.catechesis.backend.roster;

import com.catechesis.backend.roster.child.Child;
import com.catechesis.backend.roster.child.ChildRepository;
import com.catechesis.backend.roster.child.ChildSafetyInfo;
import com.catechesis.backend.roster.child.ChildSafetyInfoRepository;
import com.catechesis.backend.roster.registration.PendingRegistrationRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements LGPD right-of-erasure for the roster layer.
 *
 * <p>Given a {@link Child} id, clears the Tier-3 sensitive fields on
 * the child's paired safety info and on the originating pending
 * registration (if reachable). Identity, status, and audit metadata
 * are preserved per ADR-0003: a parish has legitimate retention
 * obligations for child-safety records, so erasure clears the
 * sensitive content while leaving the audit skeleton intact.
 *
 * <p>The redaction set today: allergies, emergency contacts,
 * parent contact email, and notes. Names are NOT cleared — they are
 * the audit's identifying information. A future
 * {@code redactIdentity()} operation may be added if a parish or
 * legal review demands stricter erasure.
 *
 * <p>This service is invoked operationally — by an integration test,
 * a one-off admin script, or (eventually) admin tooling. It is NOT
 * exposed via an HTTP endpoint in MVP. See the Sprint 2 backlog
 * (S02-12) for the rationale.
 */
@Service
public class RosterRedactionService {

    private static final Logger log = LoggerFactory.getLogger(RosterRedactionService.class);

    private final ChildRepository childRepository;
    private final ChildSafetyInfoRepository childSafetyInfoRepository;
    private final PendingRegistrationRepository pendingRegistrationRepository;

    public RosterRedactionService(
            ChildRepository childRepository,
            ChildSafetyInfoRepository childSafetyInfoRepository,
            PendingRegistrationRepository pendingRegistrationRepository) {
        this.childRepository = childRepository;
        this.childSafetyInfoRepository = childSafetyInfoRepository;
        this.pendingRegistrationRepository = pendingRegistrationRepository;
    }

    /**
     * Redacts sensitive data for a child and (when reachable) for
     * the pending registration that produced them. Idempotent:
     * re-invocation after redaction is a no-op (the entity-level
     * lifecycle methods refuse to double-stamp).
     *
     * @throws IllegalArgumentException if no child has the given id
     */
    @Transactional
    public void redactByChildId(UUID childId) {
        Child child = childRepository.findById(childId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No child with id " + childId));

        ChildSafetyInfo safetyInfo = childSafetyInfoRepository.findById(childId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No safety info for child " + childId
                                + " — data integrity violation"));

        safetyInfo.redactSensitiveData();
        // Managed entity — Hibernate dirty-flushes on transaction commit.

        UUID pendingId = child.getPendingRegistrationId();
        if (pendingId == null) {
            // Seed/import path: child was created without a back-reference.
            // Nothing to redact upstream.
            log.info("Redacted safety info for child {}; no originating "
                    + "pending registration to redact", childId);
            return;
        }

        pendingRegistrationRepository.findById(pendingId).ifPresentOrElse(
                pending -> {
                    pending.redactSensitiveData();
                    log.info("Redacted safety info and pending registration {} "
                            + "for child {}", pendingId, childId);
                },
                () -> log.warn("Pending registration {} referenced by child {} "
                                + "not found — proceeding with safety info redaction only. "
                                + "This indicates a possible data-integrity issue.",
                        pendingId, childId));
    }
}