package com.catechesis.backend.klass.dto;

/**
 * Response payload for {@code GET /classes/{id}/registration-link}.
 *
 * <p>Contains the opaque slug and the assembled public URL that a Lead
 * shares with parents. The two fields are deliberately both included
 * even though the URL is derivable from the slug — the assembly
 * (base URL + path segment) is a server concern, not a client one.
 */
public record RegistrationLinkResponse(String slug, String publicUrl) { }