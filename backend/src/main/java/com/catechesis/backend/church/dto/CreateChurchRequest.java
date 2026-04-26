package com.catechesis.backend.church.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /admin/churches}.
 *
 * <p>Records as DTOs: immutable by construction, work cleanly with
 * Jackson, and concise enough that field-level Javadoc is rarely needed.
 */
public record CreateChurchRequest(

        @NotBlank
        @Size(max = 200)
        String displayName,

        @NotBlank
        @Size(max = 64)
        String timezone

) {}