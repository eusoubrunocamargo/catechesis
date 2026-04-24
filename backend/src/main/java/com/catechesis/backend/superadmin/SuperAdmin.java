package com.catechesis.backend.superadmin;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.util.UUID;

/*
 * System-level operator. Creates Churches, bootstraps the first Lead
 * Catechist of each Church. Not tenant-scoped.
 *
 * <p>Separate from Catechist per ADR-0005 — the privilege boundary is
 * visible in the schema.
 */
@Entity
@Table(name = "super_admin")
@EntityListeners(AuditingEntityListener.class)
public class SuperAdmin {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, length = 200)
    private String email;

    @Column(nullable = false, length = 200)
    private String name;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected SuperAdmin(){}

    public SuperAdmin(String email, String name) {
        this.email = email;
        this.name = name;
    }

    public UUID getId() {
        return id;
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

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "SuperAdmin{id=" + id + ", email='" + email + "'}";
    }
}
