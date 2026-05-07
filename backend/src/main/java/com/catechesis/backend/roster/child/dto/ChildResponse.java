package com.catechesis.backend.roster.child.dto;

import com.catechesis.backend.roster.child.Child;
import com.catechesis.backend.roster.child.ChildStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Response shape for an active child on the roster. Carries Tier-2
 * identity fields only; Tier-3 safety info is accessed through a
 * dedicated audited path, never returned alongside the child's
 * identity.
 */
public record ChildResponse(
        UUID id,
        UUID klassId,
        String firstName,
        String lastName,
        ChildStatus status,
        Instant createdAt) {

    public static ChildResponse from(Child child) {
        return new ChildResponse(
                child.getId(),
                child.getKlassId(),
                child.getFirstName(),
                child.getLastName(),
                child.getStatus(),
                child.getCreatedAt());
    }
}