package com.catechesis.backend.roster.registration;

/**
 * Value type for an emergency contact attached to a registration.
 *
 * <p>This is the canonical Java representation of one element of the
 * {@code emergency_contacts} JSONB array on {@code pending_registration}
 * and {@code child_safety_info}. The same shape is reused on both sides
 * because the data is identical; the approval flow (S02-10) copies
 * contacts from the pending registration to the child's safety info.
 *
 * <p>No validation lives here — input validation is the job of the DTO
 * layer (see S02-08). By the time an {@code EmergencyContact} exists in
 * memory, its fields have already been validated or were loaded from
 * the database (where they were validated on the way in).
 */
public record EmergencyContact(String name, String phone) { }