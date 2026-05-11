-- V9__relax_parent_contact_email.sql
--
-- Drops the NOT NULL constraint on pending_registration.parent_contact_email
-- to permit LGPD redaction (S02-12) of the field.
--
-- The submission-time "email required" contract is preserved at the
-- application layer (DTO validation: @NotBlank on PublicRegistrationRequest).
-- The DB constraint was originally redundant with that validation; with
-- the redaction path now needing to clear the field, the DB constraint
-- became actively incompatible.
--
-- See S02-02 (original schema) and S02-12 (redaction service).

ALTER TABLE pending_registration
    ALTER COLUMN parent_contact_email DROP NOT NULL;