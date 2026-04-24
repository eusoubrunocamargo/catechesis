-- V5__seed_dev_super_admin.sql
--
-- Seeds the dev Super-Admin row so that DevAuthenticationFilter (S01-08)
-- has a real row to resolve the X-Dev-Super-Admin-Id header against.
--
-- Fixed UUID (all zeros + suffix) is deliberate: it's easy to type in
-- curl commands and test fixtures, and visually "screams seed value" to
-- anyone reading the data.
--
-- This migration is dev-seed only. Production Super-Admins are created
-- via the admin API after bootstrap, or via a separate prod-seed
-- migration with a different email. See ADR-0005.

INSERT INTO super_admin (id, email, name)
VALUES (
    '00000000-0000-0000-0000-000000000099',
    'eusoubrunocamargo@gmail.com',   -- REPLACE with your real operator email
    'Bruno (dev)'
)
ON CONFLICT (id) DO NOTHING;
