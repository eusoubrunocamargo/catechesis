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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * A catechesis class within a church — e.g., "Sementinha III-A" or
 * "Pequeninos". Deliberately minimal: parishes name classes differently,
 * and meeting details live on Events, not on the class itself.
 */
@Entity
@Table(name = "klass")
@EntityListeners(AuditingEntityListener.class)
public class Klass implements TenantScoped {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "church_id", nullable = false, updatable = false)
    private UUID churchId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false)
    private boolean active = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** Required by JPA. */
    protected Klass() {
    }

    public Klass(UUID churchId, String name) {
        this.churchId = churchId;
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    @Override
    public UUID getChurchId() {
        return churchId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return "Klass{id=" + id + ", churchId=" + churchId + ", name='" + name + "'}";
    }
}