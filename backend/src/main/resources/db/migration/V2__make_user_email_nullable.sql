-- Allow users created from Clerk session tokens where email/name claims are not present yet.
-- Existing rows keep their values when a later token omits a claim (upsert uses COALESCE).

ALTER TABLE users ALTER COLUMN email DROP NOT NULL;
