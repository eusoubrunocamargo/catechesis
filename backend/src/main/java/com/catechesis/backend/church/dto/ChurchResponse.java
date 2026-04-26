package com.catechesis.backend.church.dto;

import com.catechesis.backend.church.Church;
import java.time.Instant;
import java.util.UUID;

/**
 * Response body for endpoints that return a Church.
 *
 * <p>A small static factory ({@link #from(Church)}) keeps the
 * entity-to-DTO mapping in one place. As the project grows, MapStruct
 * may earn its keep — but for one or two fields, hand-mapping is
 * clearer.
 */
public record ChurchResponse(
        UUID id,
        String displayName,
        String timezone,
        Instant createdAt,
        Instant updatedAt
) {

    public static ChurchResponse from(Church church) {
        return new ChurchResponse(
                church.getId(),
                church.getDisplayName(),
                church.getTimezone(),
                church.getCreatedAt(),
                church.getUpdatedAt()
        );
    }
}