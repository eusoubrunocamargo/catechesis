package com.catechesis.backend.church;

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

/*
 * A parish / church — the tenant root. Every tenant-scoped entity
 * belongs to exactly one Church.
 *
 * <p>Church itself is NOT tenant-scoped; it IS the tenant.
 */
@Entity
@Table(name = "church")
@EntityListeners(AuditingEntityListener.class)
public class Church {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(nullable = false, length = 64)
    private String timezone;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** Required by JPA. Application code should use the public constructor. */
    protected Church() {
    }

    public Church(String displayName, String timezone) {
        this.displayName = displayName;
        this.timezone = timezone;
    }

    public UUID getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return "Church{id=" + id + ", displayName='" + displayName + "'}";
    }
}