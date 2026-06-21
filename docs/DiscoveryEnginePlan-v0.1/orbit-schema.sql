-- ============================================================
-- Orbit — Discovery Engine: Suggested High-Level Schema
-- ============================================================
-- PostgreSQL + pgvector (>= 0.8 for iterative scan).
-- This is a starting point to adapt, not a migration. Types,
-- nullability and indexes are deliberately conservative — tighten
-- per Spring Modulith module boundaries.
--
-- Tiers:           entity → source → chunk
-- Scope:           hubs ←→ entities (many-to-many)
-- Identity:        entity_identifiers  (entity resolution / dedup)
-- Search lifecycle: anfragen → matches → match_citations
-- ============================================================

create extension if not exists vector;

-- ------------------------------------------------------------
-- 1. ENTITIES  — the thing we rank and show on a card
-- ------------------------------------------------------------
create table entities (
  id              bigint primary key generated always as identity,
  entity_type     text not null,            -- 'person' | 'organization'
  display_name    text not null,
  headline        text,                     -- "CTO & Mitgründer"
  org_name        text,                     -- "Sentio Robotics GmbH, Baden"
  -- geo for the "X Min. von FHNW Brugg" chip (travel time computed at query time)
  latitude        double precision,
  longitude       double precision,
  -- "Verfügbar ab 1. Juli" — first-class because it's shown & filtered on
  available_from  date,
  status          text not null default 'active',   -- 'active' | 'hidden'
  metadata        jsonb default '{}',       -- anything not yet promoted to a column
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now()
);

-- ------------------------------------------------------------
-- 2. ENTITY IDENTIFIERS — backbone of entity resolution / dedup
-- ------------------------------------------------------------
-- When ingesting a new source, look the entity up by a stable external
-- id BEFORE creating a new entity. This is how you avoid 5 unlinked
-- fragments of the same founder.
create table entity_identifiers (
  id          bigint primary key generated always as identity,
  entity_id   bigint not null references entities(id) on delete cascade,
  id_type     text not null,    -- 'orcid' | 'github_login' | 'zefix_uid' | 'linkedin_url' | 'domain'
  id_value    text not null,
  unique (id_type, id_value)    -- one external id maps to exactly one entity
);

-- ------------------------------------------------------------
-- 3. HUBS  — scope namespaces (FHNW, accelerator, region)
-- ------------------------------------------------------------
create table hubs (
  id         bigint primary key generated always as identity,
  slug       text not null unique,          -- 'fhnw', 'startup-accelerator-aargau'
  name       text not null,
  hub_type   text,                          -- 'university' | 'accelerator' | 'region'
  -- optional anchor location (e.g. FHNW Brugg) for distance chips
  latitude   double precision,
  longitude  double precision,
  created_at timestamptz not null default now()
);

-- Many-to-many: an entity can belong to several hubs at once.
-- This is the table the hard scope filter joins to.
create table hub_memberships (
  entity_id   bigint not null references entities(id) on delete cascade,
  hub_id      bigint not null references hubs(id)     on delete cascade,
  affiliation text,                          -- 'student' | 'alumni' | 'portfolio' | 'partner'
  joined_at   timestamptz not null default now(),
  primary key (entity_id, hub_id)
);
create index on hub_memberships (hub_id);    -- scope lookups go hub -> entities

-- ------------------------------------------------------------
-- 4. SOURCES  — one evidence feed; also the citation layer
-- ------------------------------------------------------------
create table sources (
  id            bigint primary key generated always as identity,
  entity_id     bigint not null references entities(id) on delete cascade,
  source_type   text not null,    -- 'github' | 'linkedin_pdf' | 'zefix'
                                  -- | 'orcid_pub' | 'personal_site' | 'company_site'
  source_url    text,             -- citation chip target
  label         text,             -- chip text, e.g. «HIL-Testing at Scale» (Embedded World 2025)
  -- provenance & consent (revFADP / GDPR — see plan §8)
  provenance    text not null default 'harvested',  -- 'user_uploaded' | 'harvested'
  consent_basis text,             -- e.g. 'user_consent' for uploaded LinkedIn PDFs
  -- ingestion bookkeeping for incremental re-fetch & dedup
  raw_content   text,             -- optional: keep raw for re-chunking without re-fetch
  content_hash  text,             -- skip re-embedding if unchanged
  fetch_status  text default 'ok',-- 'ok' | 'error' | 'stale'
  fetched_at    timestamptz,
  created_at    timestamptz not null default now()
);
create index on sources (entity_id);

-- ------------------------------------------------------------
-- 5. CHUNKS  — the searchable unit (what hybrid search runs over)
-- ------------------------------------------------------------
create table chunks (
  id              bigint primary key generated always as identity,
  source_id       bigint not null references sources(id) on delete cascade,
  entity_id       bigint not null references entities(id),  -- denormalized for fast rollup
  chunk_index     int not null,
  content         text not null,            -- store WITHOUT the 'passage:' prefix; add at embed time
  section         text,                     -- 'abstract' | 'methods' | page slug
  token_count     int,
  fts             tsvector generated always as (to_tsvector('german', content)) stored,
  embedding       vector(1024),             -- multilingual-e5-large = 1024 dims
  -- embedding versioning: lets you re-embed with a new model without guesswork
  embedding_model text default 'multilingual-e5-large',
  embedded_at     timestamptz,
  created_at      timestamptz not null default now()
);

create index on chunks using gin  (fts);
create index on chunks using hnsw (embedding vector_ip_ops);
create index on chunks (entity_id);
create index on chunks (source_id);

-- ------------------------------------------------------------
-- 6. ANFRAGEN  — the request side (Anfrage-Board + search log)
-- ------------------------------------------------------------
-- Persisting the query (+ its embedding + chosen scope) lets you replay
-- searches, evaluate retrieval offline, and power the Anfrage lifecycle.
create table anfragen (
  id              bigint primary key generated always as identity,
  created_by      bigint,                   -- FK to your users table
  query_text      text not null,
  query_embedding vector(1024),             -- embed once, reuse
  scope_hub_ids   bigint[],                 -- which hubs the user scoped to (empty = all)
  status          text not null default 'open',  -- lifecycle: 'open' | 'matched' | 'closed'
  created_at      timestamptz not null default now()
);

-- ------------------------------------------------------------
-- 7. MATCHES  — persisted grounded-reasoning output (the cards)
-- ------------------------------------------------------------
-- Store the generated rationale so you don't regenerate on every view,
-- and so citation precision is auditable for the thesis evaluation.
create table matches (
  id           bigint primary key generated always as identity,
  anfrage_id   bigint not null references anfragen(id) on delete cascade,
  entity_id    bigint not null references entities(id) on delete cascade,
  score        double precision,            -- entity-level RRF score after rollup
  rationale    text,                         -- the German explanation shown on the card
  claims       jsonb,                        -- [{text, source_ids:[...]}] from the generator
  created_at   timestamptz not null default now(),
  unique (anfrage_id, entity_id)
);

-- The surviving (validated) citations = the chips under the rationale.
-- Normalized so you can query "which sources get cited most", etc.
create table match_citations (
  match_id   bigint not null references matches(id)  on delete cascade,
  source_id  bigint not null references sources(id)  on delete cascade,
  primary key (match_id, source_id)
);

-- ============================================================
-- WHAT CHANGED vs. a documents-only schema  (read me)
-- ============================================================
-- + hubs / hub_memberships ....... scope filtering (many-to-many)
-- + entity_identifiers ........... entity resolution / dedup backbone
-- + sources.provenance/consent ... revFADP/GDPR lawful basis
-- + sources.content_hash/status .. incremental re-ingest without re-embedding
-- + chunks.embedding_model ....... re-embed safely when you swap models
-- + chunks.token_count ........... verify chunks stay under e5's 512 limit
-- + entity.available_from/lat/lng  fields the prototype card actually shows
-- + anfragen ..................... request side + replayable search log
-- + matches / match_citations .... persist rationale + validated chips
--
-- Likely still to decide:
-- - users/accounts table (created_by FKs point at it)
-- - soft delete vs hard delete policy (status columns are in place for soft)
-- - whether claims stays jsonb or gets normalized into a match_claims table
-- ============================================================
