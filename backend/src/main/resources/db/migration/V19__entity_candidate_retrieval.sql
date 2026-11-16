-- Orbit — search: entity-level candidate retrieval
--
-- Replaces the V15 hybrid_search. The V15 version limited each retrieval leg to
-- match_count * 4 CHUNKS before grouping by entity, so a researcher with 40
-- publication chunks could fill every slot and push single-source companies out
-- (evaluation-v3: 45 of 89 expected profiles were never retrieved; ranked by
-- their best chunk, 88 of 89 sit in the top 50 entities). Its full-text leg
-- used websearch_to_tsquery, which ANDs every word of the query, so a sentence
-- query almost never matched.
--
-- This version:
--   * collapses each leg to one row per entity (its best chunk) BEFORE the
--     limit, and returns a fixed candidate depth independent of how many
--     results are shown;
--   * runs the lexical leg with OR semantics over the query's keywords, scoring
--     a chunk by the summed inverse document frequency of the terms it contains
--     (BM25 without term-frequency and length normalisation);
--   * adds an optional second semantic leg (the original-language query) next to
--     the English one;
--   * searches a per-entity profile document (name, headline, organisation,
--     summary, tags) in every leg, so profile-level text is retrievable too.
--
-- Profile documents are a search index only: they are never cited as evidence
-- and never shown as a source. ProfileDocumentService keeps them in sync.

CREATE TABLE profile_documents (
  entity_id       bigint PRIMARY KEY REFERENCES entities(id) ON DELETE CASCADE,
  content         text NOT NULL,
  embedding       vector(1024),
  embedding_model text,
  embedded_at     timestamptz,
  updated_at      timestamptz NOT NULL DEFAULT now(),
  -- Summaries and headlines are German, tags mostly English: index both stemmings.
  fts             tsvector GENERATED ALWAYS AS
                    (to_tsvector('english', content) || to_tsvector('german', content)) STORED
);

CREATE INDEX profile_documents_fts_idx ON profile_documents USING gin (fts);
CREATE INDEX profile_documents_embedding_idx ON profile_documents USING hnsw (embedding vector_ip_ops);

-- ─── Scope: the Hub → Region → CH hard filter (§5), unchanged from V10/V15 ───

CREATE FUNCTION scoped_entity_ids(scope_hub_ids bigint[], scope_region_ids bigint[])
RETURNS TABLE(entity_id bigint)
LANGUAGE sql STABLE AS $$
  SELECT e.id
  FROM entities e
  WHERE e.status = 'active'
    AND (
      (cardinality(scope_hub_ids) = 0 AND cardinality(scope_region_ids) = 0)   -- unscoped
      OR e.home_region_id = ANY(scope_region_ids)
      OR EXISTS (SELECT 1 FROM hub_memberships m
                 WHERE m.entity_id = e.id AND m.hub_id = ANY(scope_hub_ids))
    )
$$;

-- ─── Semantic leg: entities ranked by their closest chunk or profile document ───
-- chunk_pool bounds how many chunks are ranked before collapsing to entities;
-- it must stay well above candidate_count times the chunks of a prolific entity.

CREATE FUNCTION semantic_entity_ranks(
  query_embedding  vector(1024),
  scope_hub_ids    bigint[],
  scope_region_ids bigint[],
  depth            int,
  chunk_pool       int
) RETURNS TABLE(entity_id bigint, rank_ix bigint)
LANGUAGE sql STABLE AS $$
  WITH candidates AS (
    (SELECT c.entity_id, c.embedding <#> query_embedding AS distance
     FROM chunks c
     JOIN sources src ON src.id = c.source_id AND src.active          -- exclude deselected sources
     JOIN scoped_entity_ids(scope_hub_ids, scope_region_ids) sc ON sc.entity_id = c.entity_id
     WHERE c.embedding IS NOT NULL
     ORDER BY c.embedding <#> query_embedding, c.id
     LIMIT chunk_pool)
    UNION ALL
    SELECT p.entity_id, p.embedding <#> query_embedding
    FROM profile_documents p
    JOIN scoped_entity_ids(scope_hub_ids, scope_region_ids) sc ON sc.entity_id = p.entity_id
    WHERE p.embedding IS NOT NULL
  ),
  best AS (
    SELECT candidates.entity_id, min(candidates.distance) AS distance
    FROM candidates
    GROUP BY candidates.entity_id
  )
  SELECT best.entity_id, row_number() OVER (ORDER BY best.distance, best.entity_id)
  FROM best
  ORDER BY best.distance, best.entity_id
  LIMIT depth
$$;

-- ─── Lexical leg: OR over the query's terms, weighted by inverse document frequency ───
-- A chunk scores the summed IDF of the distinct query terms it contains, so a
-- chunk matching two rare terms beats one matching a single common term. The
-- query terms are the 'english' lexemes of lexical_query; each is matched as an
-- exact lexeme (no re-stemming) through the GIN index.

CREATE FUNCTION lexical_entity_ranks(
  lexical_query    text,
  scope_hub_ids    bigint[],
  scope_region_ids bigint[],
  depth            int
) RETURNS TABLE(entity_id bigint, rank_ix bigint)
LANGUAGE sql STABLE AS $$
  WITH terms AS (
    SELECT DISTINCT
           ('''' || replace(replace(t.lexeme, '\', '\\'), '''', '''''') || '''')::tsquery AS q
    FROM unnest(to_tsvector('english', coalesce(lexical_query, ''))) AS t
  ),
  corpus AS (
    SELECT ((SELECT count(*) FROM chunks) + (SELECT count(*) FROM profile_documents))::float8 AS n
  ),
  weighted AS (
    SELECT terms.q,
           ln(1.0 + (corpus.n - df.df + 0.5) / (df.df + 0.5)) AS idf
    FROM terms
    CROSS JOIN corpus
    CROSS JOIN LATERAL (
      SELECT ((SELECT count(*) FROM chunks c WHERE c.fts @@ terms.q)
            + (SELECT count(*) FROM profile_documents p WHERE p.fts @@ terms.q))::float8 AS df
    ) df
  ),
  scored AS (
    SELECT c.entity_id, sum(w.idf) AS score
    FROM chunks c
    JOIN weighted w ON c.fts @@ w.q
    JOIN sources src ON src.id = c.source_id AND src.active          -- exclude deselected sources
    JOIN scoped_entity_ids(scope_hub_ids, scope_region_ids) sc ON sc.entity_id = c.entity_id
    GROUP BY c.id, c.entity_id
    UNION ALL
    SELECT p.entity_id, sum(w.idf)
    FROM profile_documents p
    JOIN weighted w ON p.fts @@ w.q
    JOIN scoped_entity_ids(scope_hub_ids, scope_region_ids) sc ON sc.entity_id = p.entity_id
    GROUP BY p.entity_id
  ),
  best AS (
    SELECT scored.entity_id, max(scored.score) AS score
    FROM scored
    GROUP BY scored.entity_id
  )
  SELECT best.entity_id, row_number() OVER (ORDER BY best.score DESC, best.entity_id)
  FROM best
  ORDER BY best.score DESC, best.entity_id
  LIMIT depth
$$;

-- ─── Fusion: Reciprocal Rank Fusion of the entity rankings ───
-- A weight of 0 switches a leg off (pass the English vector again as
-- alt_query_embedding with weight 0 when the query was already English).
-- Returns each leg's entity rank (NULL = not in that leg's top candidate_count)
-- so the search trace can show which leg found a candidate.

DROP FUNCTION IF EXISTS hybrid_search(text, vector, bigint[], bigint[], integer, double precision, double precision, integer);

CREATE FUNCTION hybrid_search(
  lexical_query       text,
  query_embedding     vector(1024),
  alt_query_embedding vector(1024),
  scope_hub_ids       bigint[],
  scope_region_ids    bigint[],
  candidate_count     int DEFAULT 50,
  chunk_pool          int DEFAULT 2000,
  lexical_weight      double precision DEFAULT 1.0,
  semantic_weight     double precision DEFAULT 1.0,
  alt_semantic_weight double precision DEFAULT 1.0,
  rrf_k               int DEFAULT 60
) RETURNS TABLE(entity_id bigint, score double precision,
                lexical_rank int, semantic_rank int, alt_semantic_rank int)
LANGUAGE sql STABLE AS $$
WITH lexical AS (
  SELECT l.entity_id, l.rank_ix
  FROM lexical_entity_ranks(lexical_query, scope_hub_ids, scope_region_ids, candidate_count) l
  WHERE lexical_weight > 0
),
semantic AS (
  SELECT s.entity_id, s.rank_ix
  FROM semantic_entity_ranks(query_embedding, scope_hub_ids, scope_region_ids, candidate_count, chunk_pool) s
  WHERE semantic_weight > 0
),
alt_semantic AS (
  SELECT a.entity_id, a.rank_ix
  FROM semantic_entity_ranks(alt_query_embedding, scope_hub_ids, scope_region_ids, candidate_count, chunk_pool) a
  WHERE alt_semantic_weight > 0
),
ids AS (
  SELECT lexical.entity_id FROM lexical
  UNION SELECT semantic.entity_id FROM semantic
  UNION SELECT alt_semantic.entity_id FROM alt_semantic
),
fused AS (
  SELECT ids.entity_id,
         coalesce(lexical_weight / (rrf_k + l.rank_ix), 0.0)
           + coalesce(semantic_weight / (rrf_k + s.rank_ix), 0.0)
           + coalesce(alt_semantic_weight / (rrf_k + a.rank_ix), 0.0) AS score,
         l.rank_ix AS lexical_rank,
         s.rank_ix AS semantic_rank,
         a.rank_ix AS alt_semantic_rank
  FROM ids
  LEFT JOIN lexical l ON l.entity_id = ids.entity_id
  LEFT JOIN semantic s ON s.entity_id = ids.entity_id
  LEFT JOIN alt_semantic a ON a.entity_id = ids.entity_id
)
SELECT fused.entity_id, fused.score,
       fused.lexical_rank::int, fused.semantic_rank::int, fused.alt_semantic_rank::int
FROM fused
ORDER BY fused.score DESC, fused.entity_id
LIMIT candidate_count
$$;
