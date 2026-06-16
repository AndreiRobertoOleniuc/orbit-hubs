-- Identity is keyed on the Clerk user id (users.id = sub), not email.
-- Email is a mutable token claim; with no webhooks, a deleted Clerk account
-- leaves a stale row holding its email, so a new sign-up reusing that email
-- would otherwise violate UNIQUE(email) and 500 the JIT upsert (which only
-- arbitrates ON CONFLICT (id)). Clerk already enforces email uniqueness among
-- live accounts, so the DB-level constraint adds no value and only causes crashes.

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_email_key;
