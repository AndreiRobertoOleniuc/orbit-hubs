# Managed Embedding & Reranker Deployment Plan

> **Goal:** Replace local Ollama + local Python reranker microservice with managed API providers — no GPU infrastructure, no Docker containers, no cold starts to manage.

## Current State

Two GPU-dependent models run locally during development:

| Component | Model | Served by | Config key | Call sites |
|-----------|-------|-----------|------------|------------|
| Embedding | `multilingual-e5-large` (560M params, 1024 dims) | Ollama (`EmbeddingConfig` → `OllamaEmbeddingModel`) | `orbit.embedding.base-url` | `OllamaEmbeddingService.embed()` — called during ingestion (chunk embedding) and search (query embedding) |
| Reranker | `BAAI/bge-reranker-v2-m3` (568M params) | Python FastAPI (`reranker/server.py`) | `orbit.search.reranker-url` | `CrossEncoderReranker.rerank()` — called during search only (batch scores ~10–50 query-doc pairs) |

**LLM** (Gemini 2.5 Flash Lite) is already a managed API — no change needed.

### Current architecture

```
Spring Boot Backend
├── EmbeddingConfig
│   └── OllamaApi → OllamaEmbeddingModel
│       └── OllamaEmbeddingService implements EmbeddingService
│           └── .embed(text, kind) → float[1024]
└── CrossEncoderReranker
    └── RestClient → POST http://localhost:8001/rerank
        └── .rerank(query, candidates) → List<RerankResult>

Local Ollama (port 11434)     Local Python FastAPI (port 8001)
  multilingual-e5-large         bge-reranker-v2-m3
```

### Key interfaces (already abstraction-ready)

```java
// EmbeddingService.java — the interface is clean, implementation is swappable
public interface EmbeddingService {
    float[] embed(String text, EmbeddingKind kind);  // QUERY or PASSAGE
}

// CrossEncoderReranker.java — uses Spring RestClient, URL is @Value-injected
@Value("${orbit.search.reranker-url:http://localhost:8001}")
String rerankerUrl
```

### Call patterns (from code trace)

| Operation | Embedding calls | Reranker calls | Gemini calls |
|-----------|----------------|----------------|--------------|
| **Per search** | 1 (query embedding, ~50ms GPU) | 1 batch (~10–50 pairs, ~100ms GPU) | 1 translate + up to 8 rationale (parallel) |
| **Per ingestion** | 10–15 (one per chunk, ~750ms total) | 0 | 1–8 summarize + 2 enrichment |
| **Per resync** | Same as ingestion (deduped — unchanged sources skipped) | 0 | Same as ingestion |

### Traffic projection

| Phase | Searches/day | Ingestions/week | Monthly volume |
|-------|-------------|-----------------|----------------|
| Testing (now) | 5–10 | 1–2 | ~200 searches, ~10 ingestions |
| Coach sessions | 20–40 | 2–3 | ~800 searches, ~12 ingestions |
| Coaches testing | 50–100 | 5–10 | ~2000 searches, ~40 ingestions |

---

## Target State

Replace local model serving with managed API calls. No GPU, no Docker, no infrastructure.

```
Spring Boot Backend
├── EmbeddingService (interface — unchanged)
│   ├── Phase 1: HfEmbeddingService  → POST https://router.huggingface.co/hf-inference/models/intfloat/multilingual-e5-large
│   ├── Phase 2: JinaEmbeddingService → POST https://api.jina.ai/v1/embeddings
│   └── Phase 3: CohereEmbeddingService → POST https://api.cohere.com/v1/embed
│
└── CrossEncoderReranker (RestClient — URL/config changes)
    ├── Phase 1: HF Inference API  → POST https://router.huggingface.co/hf-inference/models/BAAI/bge-reranker-v2-m3
    ├── Phase 2: Jina Rerank API   → POST https://api.jina.ai/v1/rerank
    └── Phase 3: Cohere Rerank API → POST https://api.cohere.com/v1/rerank

No local Ollama. No local Python server. No GPU.
```

### Provider comparison

| | HF Serverless (Phase 1) | Jina (Phase 2) | Cohere (Phase 3) |
|---|---|---|---|
| **Embedding model** | `intfloat/multilingual-e5-large` (same) | `jina-embeddings-v3` | `embed-multilingual-v3.0` |
| **Reranker model** | `BAAI/bge-reranker-v2-m3` (same) | `jina-reranker-v2-base-multilingual` | `rerank-multilingual-v3.0` |
| **Dimensions** | 1024 (same model) | 1024 (configurable) | 1024 |
| **Re-embed needed?** | ❌ No | ✅ Yes (one-time) | ✅ Yes (one-time) |
| **Cold starts** | 10–30s possible (model unload) | None (always warm) | None (always warm) |
| **Latency** | ~200–500ms (CPU inference) | ~50–100ms (managed GPU) | ~50–100ms (managed GPU) |
| **Free tier** | $0.10/mo credits (free) / $2/mo (PRO $9) | 10M tokens | 1,000 calls/mo |
| **Embedding price** | Pay-per-compute (cheap on CPU) | $0.018/1M tokens | $0.10/1M tokens |
| **Reranker price** | Pay-per-compute | $0.018/1M tokens | $2.00/1M tokens |
| **Production-ready** | Prototyping | ✅ Yes | ✅ Enterprise-grade |

---

## Phase 1: Hugging Face Serverless (Testing)

**When:** After initial app deployment, before first external testers.
**Why:** Same exact models — no re-embedding, no quality regression, zero infrastructure cost.
**Cost:** $0 (within free tier).

### 1.1 — Get API token

1. Create Hugging Face account at https://huggingface.co
2. Settings → Access Tokens → Create new token (Read access)
3. Save token as `HF_TOKEN` in your environment

### 1.2 — New `HfEmbeddingService`

Replace `OllamaEmbeddingService` with a direct HTTP client to the HF Inference API.

**File:** `backend/src/main/java/com/orbit/app/embedding/HfEmbeddingService.java`

```java
@Service
@ConditionalOnProperty(name = "orbit.embedding.provider", havingValue = "huggingface")
class HfEmbeddingService implements EmbeddingService {

    private final RestClient hfClient;
    private final EmbeddingProperties props;

    HfEmbeddingService(
            @Value("${orbit.embedding.hf-token:${HF_TOKEN:}}") String hfToken,
            EmbeddingProperties props) {
        this.hfClient = RestClient.builder()
                .baseUrl("https://router.huggingface.co/hf-inference/models/intfloat/multilingual-e5-large")
                .defaultHeader("Authorization", "Bearer " + hfToken)
                .build();
        this.props = props;
    }

    @Override
    public float[] embed(String text, EmbeddingKind kind) {
        Assert.hasText(text, "text must not be blank");
        String prefixed = switch (kind) {
            case QUERY -> props.queryPrefix() + text;
            case PASSAGE -> props.passagePrefix() + text;
        };

        // HF feature-extraction API returns [[float, float, ...]]
        float[][] response = hfClient.post()
                .body(Map.of("inputs", prefixed))
                .retrieve()
                .body(float[][].class);

        if (response == null || response.length == 0) {
            throw new IllegalStateException("HF embedding returned empty response");
        }

        float[] vector = response[0];
        if (vector.length != props.dimensions()) {
            throw new IllegalStateException(
                    "HF embedding returned %d dims, expected %d".formatted(vector.length, props.dimensions()));
        }
        return vector;
    }
}
```

### 1.3 — New `HfRerankerClient` (or modify `CrossEncoderReranker`)

The reranker needs to call HF's `text-classification` endpoint with `text` + `text_pair` format.

**Approach:** Add a `provider` property to `CrossEncoderReranker` and branch the API call format.

```java
// Modified CrossEncoderReranker — add provider support
@Value("${orbit.search.reranker-provider:http}")
String rerankerProvider  // "http" (current Python), "huggingface", "jina", "cohere"
```

For Hugging Face, the call format is:
```json
POST https://router.huggingface.co/hf-inference/models/BAAI/bge-reranker-v2-m3
Authorization: Bearer ${HF_TOKEN}
{
  "inputs": {
    "text": "React developer SaaS",
    "text_pair": ["doc1 text", "doc2 text", ...]
  }
}
// Response: [0.82, 0.12, ...]  (raw logits per pair — apply sigmoid)
```

### 1.4 — Config changes

**`application.yaml` additions:**
```yaml
orbit:
  embedding:
    provider: ${EMBEDDING_PROVIDER:ollama}  # ollama | huggingface | jina | cohere
    # HF-specific (Phase 1)
    hf-token: ${HF_TOKEN:}
  search:
    reranker-provider: ${RERANKER_PROVIDER:http}  # http | huggingface | jina | cohere
    reranker-hf-token: ${HF_TOKEN:}
```

**Environment for deployment:**
```bash
EMBEDDING_PROVIDER=huggingface
RERANKER_PROVIDER=huggingface
HF_TOKEN=hf_your_token_here
```

### 1.5 — Conditional bean wiring

Use `@ConditionalOnProperty` to select the active `EmbeddingService`:

```java
// OllamaEmbeddingService.java — add condition
@Service
@ConditionalOnProperty(name = "orbit.embedding.provider", havingValue = "ollama", matchIfMissing = true)
class OllamaEmbeddingService implements EmbeddingService { ... }

// HfEmbeddingService.java — new
@Service
@ConditionalOnProperty(name = "orbit.embedding.provider", havingValue = "huggingface")
class HfEmbeddingService implements EmbeddingService { ... }
```

For the reranker, keep `CrossEncoderReranker` as one class but branch internally based on `rerankerProvider`. The response parsing differs by provider but the interface (`List<RerankResult>`) stays the same.

### 1.6 — Remove Ollama dependency at runtime (optional)

The `spring-ai-starter-model-ollama` Maven dependency and `EmbeddingConfig` beans can remain in the codebase (gated by `@ConditionalOnProperty`) for local development. In deployment, simply don't set `OLLAMA_BASE_URL` — the `huggingface` provider takes over.

### 1.7 — Testing checklist

- [ ] `EMBEDDING_PROVIDER=huggingface HF_TOKEN=hf_xxx ./mvnw spring-boot:run` starts without Ollama running
- [ ] Ingest a test profile (GitHub source) — chunks embed via HF, check pgvector has 1024-dim vectors
- [ ] Run a search — query embeds via HF, reranker scores via HF, results return with rationale
- [ ] Verify embedding dimensions match (1024) — no pgvector schema change
- [ ] Verify search quality is identical to local (same models, just different serving)
- [ ] Monitor HF rate limits — if hitting limits, upgrade to PRO ($9/mo) for higher quota

### 1.8 — Phase 1 cost estimate

| Item | Testing volume (200 searches, 10 ingestions) | Monthly cost |
|------|----------------------------------------------|-------------|
| HF embedding calls (~220 calls, ~50K tokens) | Within free tier | $0 |
| HF reranker calls (~200 calls, ~20K tokens) | Within free tier | $0 |
| **Total** | | **$0** |

If hitting free tier limits: HF PRO at $9/mo includes $2/mo credits + higher rate limits.

---

## Phase 2: Jina AI (Production — Cost-Optimized)

**When:** When going to production with real users, or when HF rate limits become a problem.
**Why:** Zero cold starts, guaranteed latency, cheapest per-token pricing, 1024 dims.
**Tradeoff:** Different models — requires one-time re-embedding of all chunks.

### 2.1 — Get API key

1. Sign up at https://jina.ai
2. Get API key at https://jina.ai/api-dashboard/key-manager
3. New users receive **10M free tokens**
4. Save as `JINA_API_KEY`

### 2.2 — New `JinaEmbeddingService`

**File:** `backend/src/main/java/com/orbit/app/embedding/JinaEmbeddingService.java`

```java
@Service
@ConditionalOnProperty(name = "orbit.embedding.provider", havingValue = "jina")
class JinaEmbeddingService implements EmbeddingService {

    private final RestClient jinaClient;
    private final EmbeddingProperties props;

    JinaEmbeddingService(
            @Value("${orbit.embedding.jina-key:${JINA_API_KEY:}}") String jinaKey,
            EmbeddingProperties props) {
        this.jinaClient = RestClient.builder()
                .baseUrl("https://api.jina.ai/v1")
                .defaultHeader("Authorization", "Bearer " + jinaKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
        this.props = props;
    }

    @Override
    public float[] embed(String text, EmbeddingKind kind) {
        Assert.hasText(text, "text must not be blank");

        // Jina uses task-based embedding — map our QUERY/PASSAGE to Jina's task types
        String task = switch (kind) {
            case QUERY -> "retrieval.query";
            case PASSAGE -> "retrieval.passage";
        };

        Map<String, Object> body = Map.of(
                "model", "jina-embeddings-v3",
                "input", List.of(text),
                "task", task,
                "dimensions", props.dimensions()  // 1024
        );

        JsonNode response = jinaClient.post()
                .uri("/embeddings")
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        JsonNode embeddingNode = response.path("data").path(0).path("embedding");
        float[] vector = new float[embeddingNode.size()];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) embeddingNode.get(i).asDouble();
        }

        if (vector.length != props.dimensions()) {
            throw new IllegalStateException(
                    "Jina embedding returned %d dims, expected %d".formatted(vector.length, props.dimensions()));
        }
        return vector;
    }
}
```

### 2.3 — Modify `CrossEncoderReranker` for Jina

Jina rerank API format:
```json
POST https://api.jina.ai/v1/rerank
Authorization: Bearer ${JINA_API_KEY}
{
  "model": "jina-reranker-v2-base-multilingual",
  "query": "React developer SaaS",
  "documents": ["doc1", "doc2", ...],
  "top_n": 50
}
// Response: {"results": [{"index": 0, "relevance_score": 0.92, "document": {...}}, ...]}
```

The reranker already returns sigmoid-normalized scores in [0,1] — Jina returns `relevance_score` in the same range. The `relevanceThreshold` (0.55) config stays the same.

### 2.4 — Config changes

**Environment for deployment:**
```bash
EMBEDDING_PROVIDER=jina
RERANKER_PROVIDER=jina
JINA_API_KEY=jina_your_key_here
```

### 2.5 — Re-embedding migration

**Critical:** Jina's embedding model is different from multilingual-e5-large. Existing chunk vectors in pgvector are in E5's vector space — they're incompatible with Jina query embeddings. You must re-embed ALL chunks.

**Migration script** (one-time SQL + backend operation):

1. **Null out all existing embeddings:**
   ```sql
   UPDATE chunks SET embedding = NULL WHERE embedding IS NOT NULL;
   ```

2. **Trigger resync on all entities** (either via admin API or batch script):
   - For each entity: `POST /api/v1/entities/{id}/resync`
   - The `IngestionOrchestrator.resyncEntity()` method re-fetches sources, re-chunks, and re-embeds
   - Dedup will skip unchanged source content, but since embeddings are NULL, all chunks get re-embedded
   - Actually, dedup is on source-level (content hash). If the source content hasn't changed, it skips. So we need a different approach.

   **Better approach:** Add a re-embed endpoint or script that:
   - Iterates all chunks where `embedding IS NULL`
   - Calls `embeddingService.embed(chunk.content, PASSAGE)` for each
   - Updates the row

   **Simplest approach:** Add a `POST /api/v1/admin/re-embed-all` endpoint that:
   ```java
   @Transactional
   public void reembedAll() {
       List<Chunk> nullChunks = chunks.findByEmbeddingIsNull();
       for (Chunk chunk : nullChunks) {
           float[] vec = embeddingService.embed("passage: " + chunk.getContent(), EmbeddingKind.PASSAGE);
           chunk.setEmbedding(vec);
           chunks.save(chunk);
       }
   }
   ```

3. **Also re-embed the query side** — no migration needed, queries are embedded on-the-fly at search time.

4. **Update the `embedding_model` column** in chunks to reflect the new model:
   ```sql
   UPDATE chunks SET embedding_model = 'jina-embeddings-v3' WHERE embedding_model = 'multilingual-e5-large';
   ```

### 2.6 — Testing checklist

- [ ] `EMBEDDING_PROVIDER=jina JINA_API_KEY=jina_xxx ./mvnw spring-boot:run` starts
- [ ] Embed a test query — verify 1024-dim vector returned
- [ ] Rerank a test batch — verify scores in [0,1] range
- [ ] Run re-embedding migration on existing data
- [ ] Verify all chunks have non-null embeddings after migration
- [ ] Run searches — compare quality to Phase 1 (may differ slightly due to different model)
- [ ] Check Jina rate limits (500 RPM free, 5000 RPM tier 1)
- [ ] Verify search latency (~50-100ms per embed call, ~200-500ms per rerank call)

### 2.7 — Phase 2 cost estimate

| Item | Production volume (2000 searches, 40 ingestions/mo) | Tokens | Monthly cost |
|------|-----------------------------------------------------|--------|-------------|
| Embedding (search queries) | 2000 × ~30 tokens | 60K | $0.001 |
| Embedding (ingestion chunks) | 40 × 15 chunks × ~400 tokens | 240K | $0.004 |
| Reranking | 2000 × ~20 pairs × ~100 tokens | 4M | $0.072 |
| **Total** | | | **~$0.08/mo** |

Well within the 10M free token tier. Even at 10× the volume, it's under $1/mo.

---

## Phase 3: Cohere (Enterprise — if needed)

**When:** Only if enterprise-grade SLA, compliance, or Cohere-specific features are needed.
**Why:** Most established embed+rerank API, enterprise support, AWS/Azure deployment options.
**Tradeoff:** Same as Phase 2 — different models, re-embed required. More expensive than Jina.

### 3.1 — Get API key

1. Sign up at https://dashboard.cohere.com
2. Create API key (trial key for testing, production key for production)
3. Save as `COHERE_API_KEY`

### 3.2 — New `CohereEmbeddingService`

Cohere embed API:
```json
POST https://api.cohere.com/v1/embed
Authorization: Bearer ${COHERE_API_KEY}
{
  "model": "embed-multilingual-v3.0",
  "texts": ["query: React developer"],
  "input_type": "search_query"  // or "search_document"
}
// Response: {"embeddings": [[0.01, ...1024 floats]]}
```

Cohere rerank API:
```json
POST https://api.cohere.com/v1/rerank
Authorization: Bearer ${COHERE_API_KEY}
{
  "model": "rerank-multilingual-v3.0",
  "query": "React developer SaaS",
  "documents": ["doc1", "doc2", ...],
  "top_n": 10
}
// Response: {"results": [{"index": 0, "relevance_score": 0.95}, ...]}
```

### 3.3 — Config changes

```bash
EMBEDDING_PROVIDER=cohere
RERANKER_PROVIDER=cohere
COHERE_API_KEY=cohere_your_key_here
```

### 3.4 — Phase 3 cost estimate

| Item | Production volume | Tokens | Monthly cost |
|------|-------------------|--------|-------------|
| Embedding | ~300K tokens | 300K | $0.03 |
| Reranking | ~4M tokens (query + docs) | 4M | $8.00 |
| **Total** | | | **~$8/mo** |

More expensive than Jina but still trivial. The reranker is the cost driver at $2/1M tokens.

---

## Implementation Order

```
Phase 0: Deploy app with local Ollama + Python reranker (current state)
    ↓
Phase 1: Switch to HF Serverless (same models, $0, no re-embed)
    ↓  (when: HF rate limits hit, or production launch)
Phase 2: Switch to Jina (cheapest production, re-embed once)
    ↓  (when: enterprise SLA needed, or Cohere features needed)
Phase 3: Switch to Cohere (enterprise-grade)
```

Each phase is independently deployable. The `EmbeddingService` interface and `CrossEncoderReranker` output type stay the same — only the implementation and config change.

---

## Code Change Summary

### Files to create

| File | Purpose |
|------|---------|
| `HfEmbeddingService.java` | Phase 1 — HF Inference API embedding client |
| `JinaEmbeddingService.java` | Phase 2 — Jina embeddings API client |
| `CohereEmbeddingService.java` | Phase 3 — Cohere embed API client |

### Files to modify

| File | Change |
|------|--------|
| `OllamaEmbeddingService.java` | Add `@ConditionalOnProperty(name = "orbit.embedding.provider", havingValue = "ollama", matchIfMissing = true)` |
| `EmbeddingConfig.java` | Add `@ConditionalOnProperty` to Ollama beans so they don't start when provider != ollama |
| `EmbeddingProperties.java` | Add fields: `provider`, `hfToken`, `jinaKey`, `cohereKey` |
| `CrossEncoderReranker.java` | Add `rerankerProvider` field, branch API call format and response parsing per provider |
| `application.yaml` | Add `orbit.embedding.provider`, `orbit.search.reranker-provider`, and per-provider keys |

### Files NOT changed

| File | Why |
|------|-----|
| `EmbeddingService.java` | Interface stays the same — `float[] embed(String, EmbeddingKind)` |
| `EmbeddingKind.java` | `QUERY` / `PASSAGE` enum stays the same |
| `SearchService.java` | Calls `embeddingService.embed()` and `crossEncoderReranker.rerank()` — no change |
| `IngestionOrchestrator.java` | Calls `embeddingService.embed()` — no change |
| `EntityEnrichmentService.java` | No embedding/reranking calls — no change |
| `LlmSummaryService.java` | Uses Gemini directly — no change |
| `pom.xml` | Ollama dependency stays (gated by conditional); no new Maven deps needed (RestClient is in Spring Web) |
| Flyway migrations | pgvector schema stays `vector(1024)` — all providers output 1024 dims |

---

## Environment Variables Reference

### Phase 0 (current — local dev)
```bash
OLLAMA_BASE_URL=http://localhost:11434
EMBEDDING_MODEL=jeffh/intfloat-multilingual-e5-large:f16
ORBIT_SEARCH_RERANKER_URL=http://localhost:8001
GEMINI_API_KEY=...
```

### Phase 1 (HF Serverless — testing)
```bash
EMBEDDING_PROVIDER=huggingface
RERANKER_PROVIDER=huggingface
HF_TOKEN=hf_your_token
GEMINI_API_KEY=...
# OLLAMA_BASE_URL and ORBIT_SEARCH_RERANKER_URL are ignored
```

### Phase 2 (Jina — production)
```bash
EMBEDDING_PROVIDER=jina
RERANKER_PROVIDER=jina
JINA_API_KEY=jina_your_key
GEMINI_API_KEY=...
```

### Phase 3 (Cohere — enterprise)
```bash
EMBEDDING_PROVIDER=cohere
RERANKER_PROVIDER=cohere
COHERE_API_KEY=cohere_your_key
GEMINI_API_KEY=...
```

---

## Rollback Plan

Each phase is reversible by changing environment variables:

| From | To | Action |
|------|-----|--------|
| HF Serverless | Local Ollama | Set `EMBEDDING_PROVIDER=ollama`, start Ollama locally — no data migration (same model) |
| Jina | HF Serverless | Set `EMBEDDING_PROVIDER=huggingface` — **must re-embed** (different model → E5 vectors) |
| Jina | Local Ollama | Set `EMBEDDING_PROVIDER=ollama`, start Ollama — **must re-embed** (different model → E5 vectors) |
| Cohere | Jina | Set provider to jina — **must re-embed** (different model) |

**Re-embedding is always required when switching embedding models.** The only switch that doesn't require re-embedding is between local Ollama and HF Serverless (same `multilingual-e5-large` model).

The re-embed script (from Phase 2.5) works for any model switch:
1. `UPDATE chunks SET embedding = NULL`
2. Call re-embed endpoint
3. Update `embedding_model` column

---

## Full Deployment Stack (after this change)

```
┌──────────────────────────────────────────────────────┐
│  Frontend (Vercel / Netlify / Cloudflare Pages)      │
│  React + Vite + Tailwind                              │
└────────────────────┬─────────────────────────────────┘
                     │ HTTPS
┌────────────────────▼─────────────────────────────────┐
│  Backend (Fly.io / Railway / Render / EC2)            │
│  Spring Boot 4 · Java 21                              │
│                                                       │
│  Embedding Service ──→ Managed API (HF/Jina/Cohere)  │
│  Reranker Service  ──→ Managed API (HF/Jina/Cohere)  │
│  LLM Service       ──→ Google Gemini API              │
│  Auth              ──→ Clerk                          │
└────────────────────┬─────────────────────────────────┘
                     │
┌────────────────────▼─────────────────────────────────┐
│  Database (Supabase / Neon / RDS / Fly.io Postgres)   │
│  PostgreSQL + pgvector (vector(1024) + FTS)           │
│  10 Flyway migrations (V1–V10)                        │
└──────────────────────────────────────────────────────┘
```

**No GPU infrastructure anywhere.** Everything is managed APIs or managed databases.