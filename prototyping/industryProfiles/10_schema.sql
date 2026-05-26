-- 10_schema.sql — BadenConnect v10 Discovery CLI schema
-- Nine tables, all prefixed v10_ so v10 can coexist with v8/v9.
-- Re-runnable: every object is dropped IF EXISTS before creation.
-- Core = entities + documents + inserate; the rest is the thin structured
-- layer the eventual UI demands (tags, subscriptions, users, source links).

CREATE EXTENSION IF NOT EXISTS vector;

-- Drop in dependency order (children first).
DROP TABLE IF EXISTS v10_tag_subscriptions CASCADE;
DROP TABLE IF EXISTS v10_inserat_tags     CASCADE;
DROP TABLE IF EXISTS v10_entity_tags       CASCADE;
DROP TABLE IF EXISTS v10_source_connections CASCADE;
DROP TABLE IF EXISTS v10_inserate           CASCADE;
DROP TABLE IF EXISTS v10_documents          CASCADE;
DROP TABLE IF EXISTS v10_users              CASCADE;
DROP TABLE IF EXISTS v10_tags               CASCADE;
DROP TABLE IF EXISTS v10_entities           CASCADE;

-- ─────────────────────────────────────────────────────────────────────────
-- Core: the things you match.
-- ─────────────────────────────────────────────────────────────────────────
CREATE TABLE v10_entities (
    id                  TEXT PRIMARY KEY,                 -- slug: per-marc-hofer, firm-trafosoft
    type                TEXT NOT NULL,                    -- person | company
    name                TEXT NOT NULL,
    headline            TEXT,                             -- "Senior Security Engineer · Helvetia"
    location            TEXT,                             -- free text: "Baden", "Brugg"
    location_lat        DOUBLE PRECISION,
    location_lng        DOUBLE PRECISION,
    scope               TEXT NOT NULL DEFAULT 'ch',       -- fhnw | aargau | ch — a re-rank WEIGHT, not a filter
    summary             TEXT,                             -- LLM card text; NOT embedded, NOT retrieved against
    handelsregister_uid TEXT,                             -- CHE-114.234.221 — LINDAS anchor for companies
    owner_user_id       TEXT,                             -- nullable; set when a user claims this profile
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Core: the evidence you retrieve and cite. N documents per entity.
CREATE TABLE v10_documents (
    id           TEXT PRIMARY KEY,                        -- slug: gh-mhofer-oauth21-migration
    entity_id    TEXT NOT NULL REFERENCES v10_entities(id) ON DELETE CASCADE,
    source_type  TEXT NOT NULL,                           -- github_repo|website_page|linkedin_cv|publication|github_bio|orcid_work
    source_url   TEXT,
    source_label TEXT,                                    -- footnote label: "GitHub · oauth21-migration"
    text         TEXT NOT NULL,                           -- cleaned content, <= ~4000 chars
    embedding    VECTOR(1024),                            -- multilingual-e5-large, passage: prefix
    tsv          TSVECTOR GENERATED ALWAYS AS (to_tsvector('simple', coalesce(text, ''))) STORED,
    text_hash    TEXT,                                    -- skip re-embedding if unchanged
    meta         JSONB NOT NULL DEFAULT '{}'::jsonb,      -- {stars, language, published_at, ...}
    fetched_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Core: board posts. First-class (own structured fields) AND searchable (own embedding).
CREATE TABLE v10_inserate (
    id              TEXT PRIMARY KEY,                     -- ins-marc-secrev
    kind            TEXT NOT NULL,                        -- suche | biete | open_call
    title           TEXT NOT NULL CHECK (char_length(title) >= 6),
    description     TEXT NOT NULL,
    owner_entity_id TEXT REFERENCES v10_entities(id) ON DELETE SET NULL,
    region          TEXT NOT NULL DEFAULT 'ch',           -- fhnw | aargau | ch — weight, not filter
    location        TEXT,
    location_lat    DOUBLE PRECISION,
    location_lng    DOUBLE PRECISION,
    honorar_mode    TEXT,                                 -- chf | tausch | unbezahlt
    honorar_text    TEXT,                                 -- "180/h", "Embedded-C-Coaching", "Pro Bono"
    valid_until     DATE,                                 -- past this -> auto-deactivate
    status          TEXT NOT NULL DEFAULT 'active',       -- active | expired | closed
    embedding       VECTOR(1024),
    tsv             TSVECTOR GENERATED ALWAYS AS (
                        to_tsvector('simple', coalesce(title, '') || ' ' || coalesce(description, ''))
                    ) STORED,
    text_hash       TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ─────────────────────────────────────────────────────────────────────────
-- Supporting: the thin structured layer.
-- ─────────────────────────────────────────────────────────────────────────
CREATE TABLE v10_tags (
    slug  TEXT PRIMARY KEY,                               -- "security", "oauth"
    label TEXT NOT NULL                                   -- "#Security", "#OAuth"
);

CREATE TABLE v10_inserat_tags (
    inserat_id TEXT NOT NULL REFERENCES v10_inserate(id) ON DELETE CASCADE,
    tag_slug   TEXT NOT NULL REFERENCES v10_tags(slug)    ON DELETE CASCADE,
    PRIMARY KEY (inserat_id, tag_slug)
);

CREATE TABLE v10_entity_tags (
    entity_id TEXT NOT NULL REFERENCES v10_entities(id) ON DELETE CASCADE,
    tag_slug  TEXT NOT NULL REFERENCES v10_tags(slug)   ON DELETE CASCADE,
    PRIMARY KEY (entity_id, tag_slug)
);

CREATE TABLE v10_users (
    id           TEXT PRIMARY KEY,
    email        TEXT UNIQUE NOT NULL,
    display_name TEXT,
    entity_id    TEXT REFERENCES v10_entities(id) ON DELETE SET NULL,  -- authoritative claim link
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE v10_tag_subscriptions (
    user_id  TEXT NOT NULL REFERENCES v10_users(id) ON DELETE CASCADE,
    tag_slug TEXT NOT NULL REFERENCES v10_tags(slug) ON DELETE CASCADE,
    PRIMARY KEY (user_id, tag_slug)
);

-- The onboarding "3 von 5 Quellen verbunden" rows, tracked apart from the documents they yield.
CREATE TABLE v10_source_connections (
    id             TEXT PRIMARY KEY,
    entity_id      TEXT NOT NULL REFERENCES v10_entities(id) ON DELETE CASCADE,
    source_type    TEXT NOT NULL,                         -- github | website | linkedin_cv | orcid | ...
    source_ref     TEXT,                                  -- username, url, filename
    sync_status    TEXT NOT NULL DEFAULT 'pending',       -- pending | synced | error
    last_synced_at TIMESTAMPTZ
);

-- ─────────────────────────────────────────────────────────────────────────
-- Indexes: HNSW for dense search, GIN for lexical, btree for the rollup join.
-- ─────────────────────────────────────────────────────────────────────────
CREATE INDEX v10_documents_embedding_idx ON v10_documents USING hnsw (embedding vector_cosine_ops);
CREATE INDEX v10_documents_tsv_idx       ON v10_documents USING gin  (tsv);
CREATE INDEX v10_documents_entity_idx    ON v10_documents (entity_id);

CREATE INDEX v10_inserate_embedding_idx  ON v10_inserate USING hnsw (embedding vector_cosine_ops);
CREATE INDEX v10_inserate_tsv_idx        ON v10_inserate USING gin  (tsv);
CREATE INDEX v10_inserate_owner_idx      ON v10_inserate (owner_entity_id);
