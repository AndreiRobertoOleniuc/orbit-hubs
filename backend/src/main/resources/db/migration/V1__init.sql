-- Orbit initial schema — V1
--
-- Only the implemented surface lives here: authenticated users, provisioned just-in-time from
-- verified Clerk claims. Identity is keyed on the Clerk subject (users.id = sub), not email —
-- email is a mutable, nullable token claim and carries no UNIQUE constraint. Further tables are
-- added by later migrations as their features get built.

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE users (
    id            text PRIMARY KEY,
    email         text,
    display_name  text,
    created_at    timestamptz NOT NULL DEFAULT now()
);
