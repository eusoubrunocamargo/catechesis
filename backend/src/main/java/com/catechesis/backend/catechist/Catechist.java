package com.catechesis.backend.catechist;

import com.catechesis.backend.common.tenancy.TenantScoped;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.util.UUID;

/*
 * A person authorized within a specific Church to run catechesis —
 * either a Lead (church-level administrator) or a regular Catechist.
 *
 * <p>Tenant-scoped: belongs to exactly one Church.
 */
@Entity
@Table(name = "catechist")
@EntityListeners(AuditingEntityListener.class)
public class Catechist implements TenantScoped {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "church_id", nullable = false, updatable = false)
    private UUID churchId;

    @Column(nullable = false, length = 200)
    private String email;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CatechistRole role;

    @Column(nullable = false)
    private boolean active = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /* Required by JPA. Application code should use the public constructor */
    protected Catechist() {
    }

    public Catechist(UUID churchId, String email, String name, CatechistRole role) {
        this.churchId = churchId;
        this.email = email;
        this.name = name;
        this.role = role;
    }

    public UUID getId() {
        return id;
    }

    @Override
    public UUID getChurchId() {
        return churchId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public CatechistRole getRole() {
        return role;
    }

    public void setRole(CatechistRole role) {
        this.role = role;
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
        return "Catechist{id=" + id + ", churchId=" + churchId + ", name='" + name + "', " +
                "email='" + email + "', role='" + role + "'}";
    }

}
