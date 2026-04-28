package com.catechesis.backend.klass.dto;

import com.catechesis.backend.klass.Klass;
import java.time.Instant;
import java.util.UUID;

public record KlassResponse(
        UUID id,
        UUID churchId,
        String name,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {

    public static KlassResponse from(Klass klass) {
        return new KlassResponse(
                klass.getId(),
                klass.getChurchId(),
                klass.getName(),
                klass.isActive(),
                klass.getCreatedAt(),
                klass.getUpdatedAt()
        );
    }
}