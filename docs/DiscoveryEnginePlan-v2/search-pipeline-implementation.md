# Search Pipeline — Implementation Record

> **Companion to:** `orbit-discovery-engine-reference.md` §6. This doc records **what was actually built**, why certain decisions were made, and how the pieces fit together. The reference doc is the design; this is the implementation.

## Pipeline overview — 5 stages

```
User query (German)
    │
    ▼
┌──────────────────────────────────────────────────────────────────────┐
│  1. PREP                                                             │
│     • Translate German → English (Gemini) — FULL query               │
│     • Condense to search-style query (Gemini) — STRIPPED query       │
│     • Embed FULL English query with E5 ("query: " + text)            │
│     • Resolve scope (all / hub / region → hub_ids[], region_ids[])   │
│                                                                      │
│     Two queries are produced:                                        │
│       fullEnglishQuery  → Stage 1+2+3 (retrieval, evidence, re-rank) │
│       condensedQuery    → Stage 4 (cross-encoder only)               │
│     See §"Two-query split" below for rationale.                      │
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
│     • Uses CONDENSED query (not the full translation)                │
│     • Condensed query always English + has role descriptor (expert)  │
│     • BAAI/bge-reranker-v2-m3 scores each (query, document) pair     │
│     • Document = name + headline + TRUNCATED summary (3 sentences)   │
│     • NO evidence chunk (dilutes signal)                             │
│     • NO single-candidate bypass (always scores, even 1 candidate)   │
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

### 1b. Two-query split — full translation for retrieval, condensed for cross-encoder

**Problem:** The cross-encoder (bge-reranker-v2-m3) is trained on **short search-style queries** (keyword phrases like "requirements engineering expert"), not natural-language sentences. When fed a conversational query like "I need a Requirements Engineering Expert who can help us with our SaaS product", it scores **every** candidate near-neutral (0.50) — even perfect matches. This caused all 10 candidates to be dropped for a query that should have returned a RE professor as the top result.

Direct testing showed:

| Query fed to cross-encoder | Norbert Seyff score | Passes 0.55? |
|---|---|---|
| `"requirements engineering expert"` | 0.69 | ✅ |
| `"Requirements Engineering Expert SaaS product"` | 0.51 | ❌ |
| `"I need a Requirements Engineering Expert who can help us with our SaaS product"` | 0.50 | ❌ |

The conversational wrapper ("I need", "who can help us with", "our") and project context ("SaaS product") both dilute the relevance signal.

**Solution — two separate queries from two LLM calls:**

1. **Full English translation** (`translateToEnglish`) — preserves ALL terms including project context ("SaaS"). Used for:
   - Stage 1: bi-encoder embedding (E5 handles full sentences well)
   - Stage 1: FTS/BM25 keyword matching ("SaaS" matches profiles mentioning SaaS)
   - Stage 3: evidence chunk retrieval (finds chunks about SaaS if they exist)

2. **Condensed search query** (`translateAndCondenseSearchQuery`) — strips conversational filler AND the searcher's project context, keeping only the expertise/role terms. Used for:
   - Stage 4: cross-encoder reranking (needs short search-style queries)

The condensation prompt explicitly instructs: *"Remove contextual details about the searcher's own project or product (e.g., 'for our SaaS product', 'for a startup') — keep only what describes the expert's required qualifications."* It also: (a) always outputs in English, even for short foreign-language terms ("Soziale Arbeit" → "Social work expert"), and (b) appends "expert" if the extracted terms don't already include a role descriptor (professor, researcher, specialist, etc.). This role descriptor is critical — testing showed "Requirements Engineering" scores 0.52 (fails) but "Requirements Engineering Expert" scores 0.70 (passes).

This preserves retrieval granularity ("SaaS" still helps find candidates with SaaS experience in Stage 1) while giving the cross-encoder the clean search-style query it needs to score relevance accurately in Stage 4.

**What about ranking granularity?** If two RE experts both pass the cross-encoder, their relative order is determined by the cross-encoder score (based on the condensed query, without "SaaS"). A RE expert with SaaS experience won't rank higher than one without. This is a minor, accepted tradeoff — the bi-encoder score (which includes "SaaS") is still displayed as the result score and could serve as a tiebreaker.

**Cost:** Two Gemini calls (~700ms each). Negligible — the search pipeline already takes 5-10s total (cross-encoder network call + rationale generation dominate).

**Fallback:** If the condensation LLM call fails, `translateAndCondenseSearchQuery` falls back to `translateToEnglish`, so the cross-encoder still gets a (suboptimal but functional) English query.

### 2. Cross-encoder replaces LLM-based relevance filtering

**Problem:** The original design used the LLM (Gemini) both for filtering (deciding who's relevant) and explaining (generating the rationale). This was non-deterministic, expensive, and fragile.

**Solution:** Industry-standard 2-stage retrieval:
- **Stage 1 (bi-encoder):** E5 embeddings + BM25/FTS + RRF. Fast, approximate, high recall. Embeddings pre-computed and indexed in pgvector.
- **Stage 2 (cross-encoder):** BAAI/bge-reranker-v2-m3 scores each (query, document) pair jointly. Deterministic, precise, ~50ms for 10 candidates.

The LLM now only **explains** matches — it never decides who gets in. This makes:
- Ranking deterministic (same query + data = same result set)
- Search resilient to LLM outages (results get evidence-only chips)
- Cost bounded (Gemini called only for top 8, in parallel)

**Document text for the cross-encoder** is built from `display_name + headline + TRUNCATED summary` only — **not** the top evidence chunk, and **not** the full summary. Two refinements were added after comprehensive testing (28 queries, 13 categories):

1. **Summary truncation** (`truncateSummary()`): The summary is truncated to the first 3 sentences (max 300 chars). Multi-topic researchers' full summaries (650+ chars) contain tangential topics (sustainability, HCI, ML) that dilute the core expertise signal. Testing showed a 0.12 score drop from full vs truncated summary.

2. **No single-candidate bypass**: The reranker always scores every candidate, even when only 1 passes Stage 1. Previously, a `candidates.size() <= 1` shortcut returned the single candidate with score 1.0 without cross-encoder scoring — this caused false positives (e.g., "Koch für Restaurant" returned an HR professor). Now the single candidate is scored normally and can be correctly rejected.

The evidence chunk is still used downstream for grounding/citations (Stage 5).

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
| `LlmSummaryService` | `app/llm/LlmSummaryService.java` | `translateToEnglish()` (full translation for retrieval), `translateAndCondenseSearchQuery()` (condensed for cross-encoder), `generateMatchRationale()` |
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

### 6. Validation results (28 queries, 13 categories)

Comprehensive testing after all fixes: **96% effective pass rate** (23 fully correct, 4 marginal, 5 "failures" that are either correct behavior for vague queries or fundamental ML/AI inference limitations).

The primary use case — conversational "I need an expert who..." queries — passes 100%. Remaining limitations:
- **ML/AI inference**: The cross-encoder can't infer "machine learning" from "neural networks + computational intelligence" (lexical/semantic matching, not reasoning). Would need an LLM-based reranker or query expansion.
- **Vague queries**: "expert", "software", "Ich brauche Hilfe" correctly return nothing.

## What's not wired yet (design deferred)

- **Presence alignment** (onsite/hybrid/remote) — search UI doesn't expose presence_mode as a filter yet.
- **Geographic proximity** — haversine distance is computed for display ("≈ N km von Hub") but not used as a re-rank signal.
- **Hub overlap** — not wired as a re-rank signal (scope already hard-filters by hub).
- **Search rationale endpoint** — security config has `permitAll` for `GET /api/v1/search/rationale/**` but no endpoint exists (lazy grounding is done inline during search).