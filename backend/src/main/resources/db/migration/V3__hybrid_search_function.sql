-- Orbit Discovery Engine — Stage 2: Hybrid search function
-- Reference: orbit-discovery-engine-reference.md §6.1-6.2
--
-- RRF (Reciprocal Rank Fusion) of two legs:
--   1. Full-text search (ts_rank_cd with websearch_to_tsquery)
--   2. Semantic search (pgvector inner product <#>)
-- Each leg contributes weight/(rrf_k + rank), summed. max(score) rollup per entity.
--
-- Also fixes FTS language from 'german' to 'english' since our content
-- (GitHub READMEs) is in English.

-- 1. Fix FTS language: 'german' → 'english' (our chunks are English GitHub READMEs)
DROP INDEX IF EXISTS chunks_fts_idx;
ALTER TABLE chunks DROP COLUMN IF EXISTS fts;
ALTER TABLE chunks ADD COLUMN fts tsvector generated always as (to_tsvector('english', content)) stored;
CREATE INDEX chunks_fts_idx ON chunks USING gin (fts);

-- 2. Drop any existing hybrid_search variants (in case of re-run)
DROP FUNCTION IF EXISTS hybrid_search(text, vector, bigint[], bigint[], integer, double precision, double precision, integer);
DROP FUNCTION IF EXISTS hybrid_search(text, vector, bigint[], bigint[], integer, float, float, integer);
DROP FUNCTION IF EXISTS hybrid_search(text, vector, integer, double precision, double precision, integer);
DROP FUNCTION IF EXISTS hybrid_search(text, vector, integer, float, float, integer);

-- 3. Hybrid search function (Stage 2 — no scope/hub/region filtering yet)
--    Uses double precision (not float) for weight params — Hibernate sends double precision
CREATE FUNCTION hybrid_search(
  query_text       text,
  query_embedding  vector(1024),
  match_count      int DEFAULT 10,
  full_text_weight double precision DEFAULT 1.0,
  semantic_weight  double precision DEFAULT 1.0,
  rrf_k            int DEFAULT 50
) RETURNS TABLE(entity_id bigint, score double precision)
LANGUAGE sql AS $$
WITH full_text AS (
  SELECT c.entity_id,
         row_number() OVER (
           ORDER BY ts_rank_cd(c.fts, websearch_to_tsquery('english', query_text)) DESC
         ) AS rank_ix
  FROM chunks c
  WHERE c.fts @@ websearch_to_tsquery('english', query_text)
  LIMIT match_count * 4
),
semantic AS (
  SELECT c.entity_id,
         row_number() OVER (ORDER BY c.embedding <#> query_embedding) AS rank_ix
  FROM chunks c
  WHERE c.embedding IS NOT NULL
  LIMIT match_count * 4
),
fused AS (
  SELECT coalesce(f.entity_id, s.entity_id) AS entity_id,
         coalesce(1.0/(rrf_k + f.rank_ix), 0.0) * full_text_weight +
         coalesce(1.0/(rrf_k + s.rank_ix), 0.0) * semantic_weight AS score
  FROM full_text f
  FULL OUTER JOIN semantic s ON f.entity_id = s.entity_id
)
SELECT entity_id, max(score) AS score
FROM fused
GROUP BY entity_id
ORDER BY score DESC
LIMIT match_count;
$$;