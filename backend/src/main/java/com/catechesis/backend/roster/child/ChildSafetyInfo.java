package com.catechesis.backend.roster.child;

import com.catechesis.backend.common.tenancy.TenantScoped;
import com.catechesis.backend.roster.registration.EmergencyContact;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Tier-3 safety information paired 1:1 with a {@link Child}. Created
 * by the approval flow (S02-10) at the same time as the child;
 * accessible only through this dedicated repository, never via a
 * casual roster query.
 *
 * <p>The 1:1 relationship is structural: {@code childId} is BOTH the
 * primary key of this row AND the (composite) foreign key reference
 * to {@code child}. There is no surrogate identifier; reaching this
 * row requires knowing the child's id deliberately.
 *
 * <p>Sensitive content (allergies, emergency contacts, notes) is
 * subject to LGPD right-of-erasure via {@link #redactSensitiveData()},
 * which clears the fields while preserving the row's identity and
 * the redaction timestamp.
 */
@Entity
@Table(name = "child_safety_info")
@EntityListeners(AuditingEntityListener.class)
public class ChildSafetyInfo implements TenantScoped {

    @Id
    @Column(name = "child_id", nullable = false, updatable = false)
    private UUID childId;

    @Column(name = "church_id", nullable = false, updatable = false)
    private UUID churchId;

    @Column(columnDefinition = "text")
    private String allergies;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "emergency_contacts", nullable = false, columnDefinition = "jsonb")
    private List<EmergencyContact> emergencyContacts = new ArrayList<>();

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "sensitive_data_redacted_at")
    private Instant sensitiveDataRedactedAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Required by JPA. */
    protected ChildSafetyInfo() {
    }

    /**
     * Constructs a new safety info row paired with the given child.
     * The {@code updatedAt} field is managed by the auditing listener.
     */
    public ChildSafetyInfo(
            UUID childId,
            UUID churchId,
            String allergies,
            List<EmergencyContact> emergencyContacts,
            String notes) {
        this.childId = childId;
        this.churchId = churchId;
        this.allergies = allergies;
        this.emergencyContacts =
                emergencyContacts == null ? new ArrayList<>() : new ArrayList<>(emergencyContacts);
        this.notes = notes;
    }

    /**
     * Clears Tier-3 sensitive fields per LGPD right-of-erasure.
     * Preserves identity (childId, churchId) and the redaction
     * timestamp itself. Idempotent: re-invocation after redaction is
     * a no-op (no exception, no second timestamp).
     */
    public void redactSensitiveData() {
        if (this.sensitiveDataRedactedAt != null) {
            return;
        }
        this.allergies = null;
        this.emergencyContacts = new ArrayList<>();
        this.notes = null;
        this.sensitiveDataRedactedAt = Instant.now();
    }

    public UUID getChildId() {
        return childId;
    }

    @Override
    public UUID getChurchId() {
        return churchId;
    }

    public String getAllergies() {
        return allergies;
    }

    public List<EmergencyContact> getEmergencyContacts() {
        return Collections.unmodifiableList(emergencyContacts);
    }

    public String getNotes() {
        return notes;
    }

    public Instant getSensitiveDataRedactedAt() {
        return sensitiveDataRedactedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return "ChildSafetyInfo{childId=" + childId + ", churchId=" + churchId + "}";
    }
}