package com.catechesis.backend.klass;

import com.catechesis.backend.common.tenancy.TenantScoped;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * A catechist's assignment to a class. M:N bridge between Catechist
 * and Klass. Append-only (no updated_at) — to reassign, deactivate and
 * create a new row.
 */
@Entity
@Table(name = "catechist_assignment")
@EntityListeners(AuditingEntityListener.class)
public class CatechistAssignment implements TenantScoped {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "church_id", nullable = false, updatable = false)
    private UUID churchId;

    @Column(name = "catechist_id", nullable = false, updatable = false)
    private UUID catechistId;

    @Column(name = "klass_id", nullable = false, updatable = false)
    private UUID klassId;

    @Column(nullable = false)
    private boolean active = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** Required by JPA. */
    protected CatechistAssignment() {
    }

    public CatechistAssignment(UUID churchId, UUID catechistId, UUID klassId) {
        this.churchId = churchId;
        this.catechistId = catechistId;
        this.klassId = klassId;
    }

    public UUID getId() {
        return id;
    }

    @Override
    public UUID getChurchId() {
        return churchId;
    }

    public UUID getCatechistId() {
        return catechistId;
    }

    public UUID getKlassId() {
        return klassId;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "CatechistAssignment{id=" + id + ", catechistId=" + catechistId +
                ", klassId=" + klassId + ", active=" + active + "}";
    }
}