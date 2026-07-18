-- Orbit — Feature: source curation (deselect repos / works)
-- Reference: final-features build plan §Feature 1
--
-- Each GitHub repo / OpenAlex work is its own `sources` row (with its own
-- chunks). A user may have repos/publications that no longer reflect their
-- skills; this lets them switch an individual source OFF so it stops
-- influencing search — without deleting it or re-embedding.
--
-- `active` defaults true (all existing sources stay in play). The scoped
-- hybrid_search is recreated to exclude chunks whose source is inactive, so a
-- deselected repo/work drops out of both retrieval legs. The evidence query in
-- SearchService applies the same filter so a deselected source can't surface as
-- a citation chip either.

ALTER TABLE sources ADD COLUMN active boolean NOT NULL DEFAULT true;

-- Recreate hybrid_search (V10 version) with an active-source filter inside both
-- retrieval legs. Same signature, same scope logic, same RRF/max rollup.
DROP FUNCTION IF EXISTS hybrid_search(text, vector, bigint[], bigint[], integer, double precision, double precision, integer);

CREATE FUNCTION hybrid_search(
  query_text       text,
  query_embedding  vector(1024),
  scope_hub_ids    bigint[],            -- hubs in scope (empty = no hub constraint)
  scope_region_ids bigint[],            -- region subtree in scope (empty = no region constraint)
  match_count      int DEFAULT 10,
  full_text_weight double precision DEFAULT 1.0,
  semantic_weight  double precision DEFAULT 1.0,
  rrf_k            int DEFAULT 50
) RETURNS TABLE(entity_id bigint, score double precision)
LANGUAGE sql AS $$
WITH scoped AS (                                    -- entities allowed by the hard filter
  SELECT e.id AS entity_id
  FROM entities e
  WHERE e.status = 'active'
    AND (
      (cardinality(scope_hub_ids) = 0 AND cardinality(scope_region_ids) = 0)   -- unscoped
      OR e.home_region_id = ANY(scope_region_ids)
      OR EXISTS (SELECT 1 FROM hub_memberships m
                 WHERE m.entity_id = e.id AND m.hub_id = ANY(scope_hub_ids))
    )
),
full_text AS (
  SELECT c.entity_id,
         row_number() OVER (
           ORDER BY ts_rank_cd(c.fts, websearch_to_tsquery('english', query_text)) DESC
         ) AS rank_ix
  FROM chunks c
  JOIN scoped sc ON sc.entity_id = c.entity_id
  JOIN sources src ON src.id = c.source_id AND src.active   -- exclude deselected sources
  WHERE c.fts @@ websearch_to_tsquery('english', query_text)
  LIMIT match_count * 4
),
semantic AS (
  SELECT c.entity_id,
         row_number() OVER (ORDER BY c.embedding <#> query_embedding) AS rank_ix
  FROM chunks c
  JOIN scoped sc ON sc.entity_id = c.entity_id
  JOIN sources src ON src.id = c.source_id AND src.active   -- exclude deselected sources
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
SELECT entity_id, max(score) AS score              -- MAX rollup: best signal per entity
FROM fused
GROUP BY entity_id
ORDER BY score DESC
LIMIT match_count;
$$;
