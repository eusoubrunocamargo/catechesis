package com.catechesis.backend.catechist.dto;

import com.catechesis.backend.catechist.Catechist;
import com.catechesis.backend.catechist.CatechistRole;
import java.time.Instant;
import java.util.UUID;

public record CatechistResponse(
        UUID id,
        UUID churchId,
        String email,
        String name,
        CatechistRole role,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {

    public static CatechistResponse from(Catechist catechist) {
        return new CatechistResponse(
                catechist.getId(),
                catechist.getChurchId(),
                catechist.getEmail(),
                catechist.getName(),
                catechist.getRole(),
                catechist.isActive(),
                catechist.getCreatedAt(),
                catechist.getUpdatedAt()
        );
    }
}