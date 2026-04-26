package com.catechesis.backend.catechist.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /admin/churches/{churchId}/catechists}.
 *
 * <p>The role is always LEAD and is set by the service, not by the
 * client. There is no role field here on purpose — this endpoint is
 * specifically for bootstrapping the first Lead.
 */
public record CreateLeadRequest(

        @NotBlank
        @Email
        @Size(max = 200)
        String email,

        @NotBlank
        @Size(max = 200)
        String name

) {}