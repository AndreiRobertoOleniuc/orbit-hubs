-- Orbit Discovery Engine — Stage 3: scoped hybrid search
-- Reference: orbit-discovery-engine-reference.md §5.3, §6.2
--
-- Replaces the Stage-2 (unscoped) hybrid_search with the scoped version that
-- takes two extra array params — scope_hub_ids / scope_region_ids — and applies
-- the Hub → Region → CH hard filter (§5) INSIDE both retrieval legs, before each
-- leg's LIMIT (filtering after the CTEs would let out-of-scope rows consume the
-- limit and starve a small hub — §5.3).
--
-- An entity is in scope when it is a member of a scoped hub OR its home_region_id
-- is in the scoped region subtree. Empty + empty arrays = unscoped ("Alle").
-- Also enforces status = 'active' (§7 hard filter #2). Keeps the English FTS
-- language introduced in V3 (queries are translated to English before the call).

-- Drop prior variants (Stage 2 unscoped + any earlier scoped attempt)
DROP FUNCTION IF EXISTS hybrid_search(text, vector, integer, double precision, double precision, integer);
DROP FUNCTION IF EXISTS hybrid_search(text, vector, integer, float, float, integer);
DROP FUNCTION IF EXISTS hybrid_search(text, vector, bigint[], bigint[], integer, double precision, double precision, integer);
DROP FUNCTION IF EXISTS hybrid_search(text, vector, bigint[], bigint[], integer, float, float, integer);

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
  WHERE c.fts @@ websearch_to_tsquery('english', query_text)
  LIMIT match_count * 4
),
semantic AS (
  SELECT c.entity_id,
         row_number() OVER (ORDER BY c.embedding <#> query_embedding) AS rank_ix
  FROM chunks c
  JOIN scoped sc ON sc.entity_id = c.entity_id
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
