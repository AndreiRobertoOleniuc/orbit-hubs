-- Orbit Discovery Engine — Stage 1 ingestion tables
-- Reference: orbit-discovery-engine-reference.md §14 (3.1 Core: entity → source → chunk)
--
-- These four tables are the searchable substrate. Every profile is an entity;
-- each entity has one or more sources; each source is split into chunks that
-- carry the embedding and full-text vectors.

-- entities: the matchable thing (person or organization)
create table entities (
  id              bigint primary key generated always as identity,
  entity_type     text not null,                          -- 'person' | 'organization'
  display_name    text not null,
  headline        text,
  org_name        text,
  -- home_region_id added in Stage 3 migration (ALTER TABLE)
  latitude        double precision,
  longitude       double precision,
  status          text not null default 'active',
  metadata        jsonb default '{}',
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now()
);
create index on entities (status);

-- entity_identifiers: identity resolution / dedup (one ORCID = one entity)
create table entity_identifiers (
  id          bigint primary key generated always as identity,
  entity_id   bigint not null references entities(id) on delete cascade,
  id_type     text not null,                              -- 'github_login' | 'orcid' | 'domain' | 'linkedin_url'
  id_value    text not null,
  unique (id_type, id_value)
);
create index on entity_identifiers (entity_id);

-- sources: one evidence feed per entity; also the citation layer
create table sources (
  id            bigint primary key generated always as identity,
  entity_id     bigint not null references entities(id) on delete cascade,
  source_type   text not null,                            -- 'github' | 'orcid_pub' | 'linkedin_pdf' | 'personal_site'
  source_url    text,
  label         text,                                     -- citation chip text (e.g. 'github.com/torvalds/linux')
  provenance    text not null default 'harvested',        -- 'harvested' | 'user_supplied' | 'self_declared'
  consent_basis text,                                     -- 'public_data' | 'user_consent' | 'self_declared'
  raw_content   text,                                     -- parsed text (not raw bytes for PDFs)
  content_hash  text,                                     -- sha256 of normalized core text (idempotency)
  fetch_status  text default 'ok',                        -- 'ok' | 'error' | 'skipped'
  fetched_at    timestamptz,
  created_at    timestamptz not null default now(),
  unique (source_url)
);
create index on sources (entity_id);
create index on sources (source_type);

-- chunks: the searchable unit (embedding + fts)
create table chunks (
  id              bigint primary key generated always as identity,
  source_id       bigint not null references sources(id) on delete cascade,
  entity_id       bigint not null references entities(id),  -- denormalized for scoped filtering
  chunk_index     int not null,
  content         text not null,
  section         text,                                    -- context label (e.g. 'Features', 'Embedded Systems')
  token_count     int,
  fts             tsvector generated always as (to_tsvector('german', content)) stored,
  embedding       vector(1024),
  embedding_model text default 'multilingual-e5-large',
  embedded_at     timestamptz,
  created_at      timestamptz not null default now(),
  unique (source_id, chunk_index)
);
create index on chunks using gin  (fts);
create index on chunks using hnsw (embedding vector_ip_ops);
create index on chunks (entity_id);
create index on chunks (source_id);