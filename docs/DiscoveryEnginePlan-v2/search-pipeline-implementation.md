# Search Pipeline — Implementation Record

> **Companion to:** `orbit-discovery-engine-reference.md` §6. This doc records **what was actually built**, why certain decisions were made, and how the pieces fit together. The reference doc is the design; this is the implementation.

## Pipeline overview — 5 stages

```
User query (German)
    │
    ▼
┌──────────────────────────────────────────────────────────────────────┐
│  1. PREP                                                             │
│     • Translate German → English (Gemini)                            │
│     • Embed English query with E5 ("query: " + text)                 │
│     • Resolve scope (all / hub / region → hub_ids[], region_ids[])   │
└──────────────────────────────────────────────────────────────────────┘
    │
    ▼
┌──────────────────────────────────────────────────────────────────────┐
│  2. BI-ENCODER RETRIEVAL  (SearchService → hybrid_search SQL)        │
│     • FTS (English) + pgvector semantic, fused by RRF                │
│     • Hard scope filter inside both CTEs (Hub/Region/CH)             │
│     • MAX rollup per entity, top match_count                         │
│     • Fetch top-k evidence chunks per entity (deduped by source)     │
└──────────────────────────────────────────────────────────────────────┘
    │
    ▼
┌──────────────────────────────────────────────────────────────────────┐
│  3. SOFT RE-RANK  (RerankService)                                    │
│     • Min-max normalize base RRF scores                              │
│     • + W_avail * availabilityOverlap (date window + Pensum)         │
│     • + W_open  * openToRequests flag                                │
│     • Filter: drop candidates below similarity threshold (0.80)      │
└──────────────────────────────────────────────────────────────────────┘
    │
    ▼
┌──────────────────────────────────────────────────────────────────────┐
│  4. CROSS-ENCODER RERANKING  (CrossEncoderReranker → Python :8001)   │
│     • BAAI/bge-reranker-v2-m3 scores each (query, document) pair     │
│     • Drop candidates below relevance threshold (0.55)               │
│     • Reorder by cross-encoder score                                 │
│     • Graceful fallback if service is down                           │
└──────────────────────────────────────────────────────────────────────┘
    │
    ▼
┌──────────────────────────────────────────────────────────────────────┐
│  5. LLM GROUNDING + RENDER  (LlmSummaryService → Gemini Flash)       │
│     • Parallelized: top rationaleTopN (8) results get Gemini call    │
│     • Remaining results: rationale=null, evidence-only chips         │
│     • Citation validation: drop hallucinated claims                  │
│     • Assemble SearchResultDto with rationale, chips, distance       │
└──────────────────────────────────────────────────────────────────────┘
    │
    ▼
SearchResponse { query, sufficient: true/false, results: [SearchResult] }
```

## Key implementation decisions

### 1. English FTS with query translation

**Problem:** Entity summaries and chunk content are generated in English by the LLM enrichment pipeline. German FTS (`to_tsvector('german', ...)`) would poorly match English content due to compound words and morphology differences.

**Solution:** 
- The `hybrid_search` SQL function uses `websearch_to_tsquery('english', ...)` (V10 migration).
- Before retrieval, the user's German query is translated to English by Gemini (`LlmSummaryService.translateToEnglish()`).
- The English query is used for both FTS and semantic embedding.
- The rationale (stage 5) is generated from the **original German query**, so the user-facing explanation stays in their language.

**Cost:** ~200ms for the translation call. The retrieval path operates in English; the explanation path operates in German.

### 2. Cross-encoder replaces LLM-based relevance filtering

**Problem:** The original design used the LLM (Gemini) both for filtering (deciding who's relevant) and explaining (generating the rationale). This was non-deterministic, expensive, and fragile.

**Solution:** Industry-standard 2-stage retrieval:
- **Stage 1 (bi-encoder):** E5 embeddings + BM25/FTS + RRF. Fast, approximate, high recall. Embeddings pre-computed and indexed in pgvector.
- **Stage 2 (cross-encoder):** BAAI/bge-reranker-v2-m3 scores each (query, document) pair jointly. Deterministic, precise, ~50ms for 10 candidates.

The LLM now only **explains** matches — it never decides who gets in. This makes:
- Ranking deterministic (same query + data = same result set)
- Search resilient to LLM outages (results get evidence-only chips)
- Cost bounded (Gemini called only for top 8, in parallel)

### 3. Similarity threshold as a hard cut

After soft re-rank, candidates whose best evidence-chunk cosine similarity is below `orbit.search.similarity-threshold` (default 0.80) are dropped. This prevents weak matches from reaching the user. If all candidates fall below, `sufficient=false` and the frontend prompts the user to widen scope or create an Anfrage.

### 4. Parallelized grounding

The Gemini grounding calls for the top `rationaleTopN` (default 8) results are dispatched as parallel `CompletableFuture`s, then joined. This turns 8 sequential ~1s LLM calls into one ~1s wait. Results beyond `rationaleTopN` get `rationale=null` and fall back to evidence-only chips.

### 5. Graceful degradation at every stage

| Failure | Behavior |
|---------|----------|
| Translation fails | Falls back to raw query text (search still works, quality may degrade) |
| Reranker service down | Returns all candidates in pre-rerank order (search still works) |
| Gemini grounding fails | Returns evidence-only chips, `rationale=null` (search still works) |
| All candidates fail similarity threshold | `sufficient=false`, frontend prompts to widen/create Anfrage |
| All candidates fail cross-encoder | `sufficient=false`, same prompt |

Search never throws — it degrades.

## Components

| Component | File | Role |
|-----------|------|------|
| `SearchController` | `app/search/SearchController.java` | Thin HTTP adapter, `POST /api/v1/search` |
| `SearchService` | `app/search/SearchService.java` | Orchestrates the 5-stage pipeline |
| `ScopeResolver` | `app/search/ScopeResolver.java` | Resolves scope selection → `hub_ids[]`, `region_ids[]` (walks region tree in-app) |
| `RerankService` | `app/search/RerankService.java` | Soft re-rank: availability overlap + open-boost on normalized RRF |
| `RerankProperties` | `app/search/RerankProperties.java` | Config: `availabilityWeight=0.3`, `openToRequestsWeight=0.1` |
| `CrossEncoderReranker` | `app/search/CrossEncoderReranker.java` | Calls Python microservice, drops irrelevant candidates |
| `LlmSummaryService` | `app/llm/LlmSummaryService.java` | `translateToEnglish()`, `generateMatchRationale()` |
| `EmbeddingService` | `app/embedding/EmbeddingService.java` | Ollama E5 embeddings (`embed(text, QUERY/PASSAGE)`) |
| `hybrid_search()` | `V10__scoped_hybrid_search.sql` | SQL function: FTS + pgvector + RRF with scope hard filter |

### Python reranker microservice

A separate FastAPI service (not in the Spring Boot codebase) running on port 8001:
- Loads `BAAI/bge-reranker-v2-m3` via HuggingFace Transformers
- Endpoint: `POST /rerank` with `{"query": "...", "documents": ["...", "..."]}`
- Returns: `{"results": [{"index": 0, "score": 0.87}, ...]}`
- Scores are sigmoid-normalized to [0, 1]

## Config

| Property | Default | Purpose |
|----------|---------|---------|
| `orbit.search.similarity-threshold` | `0.80` | Min best-evidence similarity to keep a candidate |
| `orbit.search.rationale-top-n` | `8` | How many top results get a Gemini rationale |
| `orbit.search.reranker-url` | `http://localhost:8001` | Python cross-encoder service URL |
| `orbit.search.reranker-threshold` | `0.55` | Min cross-encoder score to keep a candidate |
| `orbit.search.rerank.availability-weight` | `0.3` | Soft re-rank weight for availability overlap |
| `orbit.search.rerank.open-to-requests-weight` | `0.1` | Soft re-rank weight for open-to-requests flag |

## Migrations

| Migration | What it creates |
|-----------|----------------|
| `V3__hybrid_search_function.sql` | Initial unscoped `hybrid_search` (FTS + pgvector + RRF) |
| `V10__scoped_hybrid_search.sql` | Replaces V3 with scoped version (adds `scope_hub_ids`, `scope_region_ids` params, `scoped` CTE, English FTS) |

## What's not wired yet (design deferred)

- **Presence alignment** (onsite/hybrid/remote) — search UI doesn't expose presence_mode as a filter yet.
- **Geographic proximity** — haversine distance is computed for display ("≈ N km von Hub") but not used as a re-rank signal.
- **Hub overlap** — not wired as a re-rank signal (scope already hard-filters by hub).
- **Search rationale endpoint** — security config has `permitAll` for `GET /api/v1/search/rationale/**` but no endpoint exists (lazy grounding is done inline during search).