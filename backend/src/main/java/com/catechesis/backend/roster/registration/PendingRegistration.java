package com.catechesis.backend.roster.registration;

import com.catechesis.backend.common.tenancy.TenantScoped;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A parent's submission to enroll a child in a class, captured before
 * a Lead has reviewed it. Lifecycle: PENDING on submission, then
 * APPROVED or REJECTED on review. Approved registrations produce an
 * active {@link com.catechesis.backend.roster.child.Child}; rejected
 * ones do not.
 *
 * <p>This row is the consent ledger. It is never deleted: redaction
 * (LGPD right-of-erasure, see S02-12) clears sensitive fields but
 * preserves the audit skeleton (id, status, review metadata, consent
 * version, timestamps).
 *
 * <p>Mutation is funneled through the lifecycle methods
 * ({@link #approve}, {@link #reject}, {@link #redactSensitiveData})
 * rather than through field setters. This is deliberate: each transition
 * has invariants ("approval means status, reviewer, and timestamp all
 * move together") that the entity defends, so the service layer can't
 * accidentally write a half-completed transition.
 */
@Entity
@Table(name = "pending_registration")
public class PendingRegistration implements TenantScoped {

    // ----- Identity -----

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "church_id", nullable = false, updatable = false)
    private UUID churchId;

    @Column(name = "klass_id", nullable = false, updatable = false)
    private UUID klassId;

    // ----- Status -----

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RegistrationStatus status;

    // ----- Submitted child identity (Tier-2 PII) -----

    @Column(name = "child_first_name", nullable = false, length = 200)
    private String childFirstName;

    @Column(name = "child_last_name", nullable = false, length = 200)
    private String childLastName;

    // ----- Submitted safety info (Tier-3 PII) -----

    @Column(columnDefinition = "text")
    private String allergies;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "emergency_contacts", nullable = false, columnDefinition = "jsonb")
    private List<EmergencyContact> emergencyContacts = new ArrayList<>();

    // ----- Submitted parent contact (Tier-2 PII) -----

    @Column(name = "parent_contact_email", nullable = false, length = 200)
    private String parentContactEmail;

    // ----- Consent capture (write-once; preserved through redaction) -----

    @Column(name = "consent_version", nullable = false, updatable = false, length = 20)
    private String consentVersion;

    @Column(name = "consent_granted_at", nullable = false, updatable = false)
    private Instant consentGrantedAt;

    // ----- Submission timestamp -----

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    // ----- Review metadata (written once at review, never reverted) -----

    @Column(name = "reviewed_by_catechist_id")
    private UUID reviewedByCatechistId;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    // ----- LGPD redaction marker -----

    @Column(name = "sensitive_data_redacted_at")
    private Instant sensitiveDataRedactedAt;

    /** Required by JPA. */
    protected PendingRegistration() {
    }

    /**
     * Constructs a new submission. Status is initialized to
     * {@link RegistrationStatus#PENDING}; review fields are null
     * until {@link #approve} or {@link #reject} is called.
     */
    public PendingRegistration(
            UUID id,
            UUID churchId,
            UUID klassId,
            String childFirstName,
            String childLastName,
            String allergies,
            List<EmergencyContact> emergencyContacts,
            String parentContactEmail,
            String consentVersion,
            Instant consentGrantedAt) {
        this.id = id;
        this.churchId = churchId;
        this.klassId = klassId;
        this.status = RegistrationStatus.PENDING;
        this.childFirstName = childFirstName;
        this.childLastName = childLastName;
        this.allergies = allergies;
        this.emergencyContacts =
                emergencyContacts == null ? new ArrayList<>() : new ArrayList<>(emergencyContacts);
        this.parentContactEmail = parentContactEmail;
        this.consentVersion = consentVersion;
        this.consentGrantedAt = consentGrantedAt;
    }

    @PrePersist
    private void onSubmit() {
        if (this.submittedAt == null) {
            this.submittedAt = Instant.now();
        }
    }

    // ----- Lifecycle methods -----

    /**
     * Marks this registration as approved by the given catechist.
     * Pre-condition: status is {@link RegistrationStatus#PENDING}.
     *
     * @throws IllegalStateException if the registration is not pending
     */
    public void approve(UUID reviewerCatechistId) {
        requirePending("approve");
        this.status = RegistrationStatus.APPROVED;
        this.reviewedByCatechistId = reviewerCatechistId;
        this.reviewedAt = Instant.now();
    }

    /**
     * Marks this registration as rejected by the given catechist.
     * Pre-condition: status is {@link RegistrationStatus#PENDING}.
     *
     * @param rejectionReason optional human-readable reason; may be null
     * @throws IllegalStateException if the registration is not pending
     */
    public void reject(UUID reviewerCatechistId, String rejectionReason) {
        requirePending("reject");
        this.status = RegistrationStatus.REJECTED;
        this.reviewedByCatechistId = reviewerCatechistId;
        this.reviewedAt = Instant.now();
        this.rejectionReason = rejectionReason;
    }

    /**
     * Clears Tier-2/3 sensitive fields per LGPD right-of-erasure.
     * Preserves identity, status, review metadata, consent version,
     * and timestamps so the audit skeleton survives.
     *
     * <p>Idempotent at the application level: re-invoking on an
     * already-redacted row is a no-op (no exception, no second
     * timestamp). The service layer (S02-12) decides whether
     * re-redaction is allowed; the entity simply doesn't double-stamp.
     */
    public void redactSensitiveData() {
        if (this.sensitiveDataRedactedAt != null) {
            return;
        }
        this.allergies = null;
        this.emergencyContacts = new ArrayList<>();
        this.parentContactEmail = null;
        this.sensitiveDataRedactedAt = Instant.now();
    }

    private void requirePending(String operation) {
        if (this.status != RegistrationStatus.PENDING) {
            throw new IllegalStateException(
                    "Cannot " + operation + " a registration in status " + this.status);
        }
    }

    // ----- Accessors (no setters; mutation goes through lifecycle methods) -----

    public UUID getId() {
        return id;
    }

    @Override
    public UUID getChurchId() {
        return churchId;
    }

    public UUID getKlassId() {
        return klassId;
    }

    public RegistrationStatus getStatus() {
        return status;
    }

    public String getChildFirstName() {
        return childFirstName;
    }

    public String getChildLastName() {
        return childLastName;
    }

    public String getAllergies() {
        return allergies;
    }

    public List<EmergencyContact> getEmergencyContacts() {
        return Collections.unmodifiableList(emergencyContacts);
    }

    public String getParentContactEmail() {
        return parentContactEmail;
    }

    public String getConsentVersion() {
        return consentVersion;
    }

    public Instant getConsentGrantedAt() {
        return consentGrantedAt;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public UUID getReviewedByCatechistId() {
        return reviewedByCatechistId;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public Instant getSensitiveDataRedactedAt() {
        return sensitiveDataRedactedAt;
    }

    // ----- toString -----

    @Override
    public String toString() {
        return "PendingRegistration{id=" + id
                + ", churchId=" + churchId
                + ", klassId=" + klassId
                + ", status=" + status + "}";
    }
}