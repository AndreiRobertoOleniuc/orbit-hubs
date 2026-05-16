-- 002_unified_chunks.sql
-- Generalized entity + chunks schema for the BadenConnect prototype.
-- Adds: persons, publications, chunks, person_identities.
-- Backfills existing companies.embedding rows into chunks
-- (entity_type='company', source='lindas', chunk_kind='zweck').
-- Old companies.embedding / embedded_at columns are intentionally LEFT IN PLACE
-- so the backfill can be verified before dropping them manually.

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS persons (
  id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  display_name      TEXT        NOT NULL,
  primary_locality  TEXT,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS publications (
  id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  doi          TEXT        UNIQUE,
  openalex_id  TEXT        UNIQUE,
  title        TEXT        NOT NULL,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS person_identities (
  person_id    UUID  NOT NULL REFERENCES persons(id) ON DELETE CASCADE,
  source       TEXT  NOT NULL
    CHECK (source IN ('github','linkedin','website','orcid','openalex','irf_fhnw','wikidata')),
  external_id  TEXT  NOT NULL,
  linked_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (source, external_id)
);
CREATE INDEX IF NOT EXISTS person_identities_person_id_idx ON person_identities (person_id);

CREATE TABLE IF NOT EXISTS chunks (
  id           BIGSERIAL    PRIMARY KEY,
  entity_type  TEXT         NOT NULL
    CHECK (entity_type IN ('company','person','publication')),
  entity_id    TEXT         NOT NULL,
  source       TEXT         NOT NULL
    CHECK (source IN ('lindas','wikidata','github','linkedin','website','orcid','openalex','irf_fhnw')),
  chunk_kind   TEXT         NOT NULL,
  chunk_label  TEXT         NOT NULL,
  content      TEXT         NOT NULL,
  embedding    vector(1024),
  embedded_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  source_url   TEXT,
  metadata     JSONB        NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX IF NOT EXISTS chunks_entity_source_idx ON chunks (entity_type, entity_id, source);
CREATE INDEX IF NOT EXISTS chunks_source_idx        ON chunks (source);

-- Backfill existing company embeddings, idempotent via NOT EXISTS guard.
INSERT INTO chunks (entity_type, entity_id, source, chunk_kind, chunk_label,
                    content, embedding, embedded_at, source_url, metadata)
SELECT
  'company',
  c.id::text,
  'lindas',
  'zweck',
  c.legal_name,
  c.zweck,
  c.embedding,
  COALESCE(c.embedded_at, now()),
  NULL,
  jsonb_build_object('uid', c.uid, 'company_uri', c.company_uri)
FROM companies c
WHERE c.embedding IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM chunks ch
    WHERE ch.entity_type = 'company'
      AND ch.entity_id   = c.id::text
      AND ch.source      = 'lindas'
      AND ch.chunk_kind  = 'zweck'
  );

COMMIT;
