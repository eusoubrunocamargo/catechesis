package com.catechesis.backend.klass.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /classes}.
 *
 * <p>No churchId field — tenant identity comes from the authenticated
 * Lead, not from user input.
 */
public record CreateKlassRequest(

        @NotBlank
        @Size(max = 200)
        String name

) {}