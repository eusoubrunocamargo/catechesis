package com.catechesis.backend.roster.child;

import com.catechesis.backend.common.tenancy.TenantScoped;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * An active child on a class's roster. Created by the approval flow
 * (S02-10) when a Lead promotes a {@code PendingRegistration}; never
 * deleted thereafter. Status transitions through
 * {@link ChildStatus#ACTIVE}, {@link ChildStatus#INACTIVE}, and
 * {@link ChildStatus#GRADUATED} over the catechesis cycle.
 *
 * <p>Tier-3 sensitive info (allergies, emergency contacts, notes) is
 * stored in a paired {@code ChildSafetyInfo} row, accessed only
 * through its dedicated repository. Tier-2 identity (name, klass)
 * lives here.
 *
 * <p>For Sprint 2, this entity is write-once-at-construction: no
 * setters, no lifecycle methods. Mutation endpoints arrive in later
 * sprints and will introduce the appropriate domain operations
 * (e.g., {@code markInactive}, {@code reassignToKlass}).
 */
@Entity
@Table(name = "child")
@EntityListeners(AuditingEntityListener.class)
public class Child implements TenantScoped {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "church_id", nullable = false, updatable = false)
    private UUID churchId;

    @Column(name = "klass_id", nullable = false)
    private UUID klassId;

    @Column(name = "first_name", nullable = false, length = 200)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 200)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChildStatus status;

    @Column(name = "pending_registration_id", updatable = false)
    private UUID pendingRegistrationId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Required by JPA. */
    protected Child() {
    }

    /**
     * Constructs a new active child. Status is initialized to
     * {@link ChildStatus#ACTIVE}; created/updated timestamps are
     * managed by the auditing listener.
     *
     * @param pendingRegistrationId the originating registration's id;
     *                              may be null only for non-approval
     *                              creation paths (none exist today)
     */
    public Child(
            UUID id,
            UUID churchId,
            UUID klassId,
            String firstName,
            String lastName,
            UUID pendingRegistrationId) {
        this.id = id;
        this.churchId = churchId;
        this.klassId = klassId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.status = ChildStatus.ACTIVE;
        this.pendingRegistrationId = pendingRegistrationId;
    }

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

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public ChildStatus getStatus() {
        return status;
    }

    public UUID getPendingRegistrationId() {
        return pendingRegistrationId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return "Child{id=" + id
                + ", churchId=" + churchId
                + ", klassId=" + klassId
                + ", status=" + status + "}";
    }
}