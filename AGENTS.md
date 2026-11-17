# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

> `CLAUDE.md` is a byte-identical mirror of this file (except the header) for other agents.
> **When you change this file, apply the same change to `CLAUDE.md`.**

## Project Overview

**Orbit** — semantic matchmaking platform for the Swiss innovation ecosystem (FHNW P5; client: PowerPlant / Baden 4.0). Asymmetric demand/supply platform:

- **Supply side** — profiles auto-generated from linked sources (GitHub, OpenAlex, LinkedIn PDF, Zefix, websites, manual text). Users set availability ("Offen für Anfragen"), subscribe to topics, join hubs.
- **Demand side** — free-text semantic search returning ranked people/companies with a German rationale and cited evidence. When search finds nothing sufficient, the seeker creates an **Anfrage** (request) that lands on the public board and is pushed to matching profiles.

**Canonical spec:** `../TechnicalImplementationDocs/Specs/Orbit_Specv2.md`. The `§` references in Javadoc throughout the backend point at `../TechnicalImplementationDocs/DiscoveryEnginePlan/orbit-discovery-engine-reference.md`.

Both surfaces are **built and functional end-to-end** — supply onboarding/profile, search, Anfrage lifecycle (create → distribute → escalate → contact → Postfach), and admin hub management.

## Commands

### Backend (`backend/`)

```bash
./mvnw spring-boot:run                    # Start backend (http://localhost:8080)
./mvnw test                               # All tests
./mvnw test -Dtest=SearchServiceTest      # Single test class
./mvnw test -Dtest=SearchServiceTest#name # Single test method
./mvnw verify                             # Full build + tests
```

**Postgres must be started manually first** — `docker compose -f backend/compose.yaml up -d` (pgvector/pg16, db `badenconnect`). Flyway runs migrations on boot; Hibernate is `ddl-auto: validate` and never mutates the schema.

### Frontend (`frontend/`)

```bash
npm run dev                       # Vite dev server (http://localhost:5173)
npm run test                      # vitest run
npx vitest run src/lib/utils.test.ts   # Single test file
npx vitest                        # Watch mode
npm run build                     # tsc -b && vite build
npm run typecheck                 # tsc -b (type-checks the app and node projects)
npm run lint                      # ESLint
npm run format                    # Prettier write (no semicolons, double quotes)
```

Required env: `VITE_CLERK_PUBLISHABLE_KEY`; optional `VITE_API_BASE_URL` (default `http://localhost:8080`).

### Deploy (`k8s/`)

```bash
VITE_CLERK_PUBLISHABLE_KEY='pk_...' ./k8s/deploy.sh all   # or: backend | frontend
```

Builds multi-arch images, pushes to Docker Hub, applies `k8s/orbit.yml` to k3s. There is no CI pipeline in the repo — deployment is driven from a laptop.

## Inference providers — read before touching search or embeddings

**Every model call goes through OpenRouter** — one key (`OPENROUTER_API_KEY`), one bill, three endpoints, all behind `app.inference.OpenRouterClient`:

| Concern | Model (`orbit.openrouter.*` in `application.yaml`) | Class |
|---|---|---|
| LLM (summaries, tags, query rewrite, rationale) | `chat.model` `google/gemini-2.5-flash-lite`, then `chat.fallback-models` | `llm.OpenRouterChatModel` (a Spring AI `ChatModel`) |
| Embeddings (1024-dim) | `embedding.model` `mongodb/voyage-4-lite` | `embedding.OpenRouterEmbeddingService` |
| Reranker | `reranker.model` `cohere/rerank-5-fast` | `search.OpenRouterCrossEncoderClient` |

There is no other provider and no local model. `OPENROUTER_API_KEY` is required to start. Spring AI is used only for its `ChatModel`/`Prompt` types (`spring-ai-model`); nothing is auto-configured.

**Configuration.** `backend/src/main/resources/application.yaml` holds three things: the **model choices and the values tied to them** (models, LLM fallbacks, temperature and token limit, embedding size and input types, data-collection policy, `orbit.search.reranker-threshold`, `orbit.tags.canonical-similarity`), the credentials the deployment passes in under its own names, and what differs from Spring's defaults. The model settings have **no default in code** — the YAML is their only source, and `OpenRouterProperties` stops the start when one is missing; `OpenRouterPropertiesTest` pins the production models. Every other tunable (depths, budgets, retries, rate limits) has its default next to the code that reads it (`@Value` defaults, `RerankProperties`, `RequestsProperties`). `ApplicationYamlTest` fails on any other YAML key: don't add one only to repeat a code default. Any setting can be overridden by environment variable under Spring's naming (`orbit.search.reranker-threshold` → `ORBIT_SEARCH_RERANKER_THRESHOLD`). Every request carries `provider.data_collection` (`orbit.openrouter.data-collection`, default `deny`), so profile text only goes to providers that do not keep it.

**Three settings belong to a model and must change with it:**
- **Reranker threshold** (`orbit.search.reranker-threshold`): scores of different rerankers are not comparable — 0.3 for cohere/rerank-5-fast. A new reranker needs an `evaluation-v3` run with `EVAL_SWEEP_THRESHOLDS`.
- **How the embedding model tells a query from a passage**: `input_type` (`orbit.openrouter.embedding.query-input-type` / `passage-input-type`, `search_query` / `search_document` for voyage) or a text prefix (`orbit.openrouter.embedding.query-prefix` / `passage-prefix`, `query: ` / `passage: ` for e5 — empty by default).
- **Tag similarity floor** (`orbit.tags.canonical-similarity`): 0.78 for voyage-4-lite (0.95 was right for e5).

**Changing the embedding model needs only a restart.** Every vector is stored with `EmbeddingService.modelName()`; after start-up, chunks, tags and profile documents carrying another name are re-embedded in the background (56 profiles: under a minute). Until that finishes, search compares new query vectors with old chunk vectors.

**The LLM needs its fallback models.** gemini-2.5-flash-lite has one provider on OpenRouter and a rate limit shared by all OpenRouter customers: 24 of 60 calls in bursts of 20 were refused with HTTP 429 (2026-11-16). With `chat.fallback-models` OpenRouter answers from the next model inside the same request; with them 198 evaluation searches ran without one retry. Do not remove them without a burst test. On the evaluation set Gemini 3.1 Flash-Lite and Claude Haiku 5.5 rewrote queries as well as 2.5 Flash-Lite.

**Measure search latency against OpenRouter.** Deployment runs on small ARM machines with every model hosted. Measured on OpenRouter (2026-11-16, 198 searches): query rewrite 0.5 s, two query embeddings 0.2 s, reranker 0.36 s for 30 cards — a search takes about 1.1 s (p90 1.4 s), a repeated one 0.4 s (rewrite and vectors are cached). One search's embedding and reranker calls share `orbit.search.inference-budget` (30 s, `InferenceDeadline`), after which the search answers 503; an LLM call waits at most `orbit.openrouter.chat.max-wait` (20 s) and the search then uses the query as typed; background work waits `orbit.openrouter.max-wait` (2 min). `CrossEncoderReranker` logs passages, characters and time per call. Before OpenRouter the same search took 3–7 s on Hugging Face serverless and regularly ran into the 30 s budget.

## The search pipeline (`SearchService`)

```
Query
 → LLM prep (`SearchQueryRewriter`): full English translation (semantic leg) + condensed search-style
   terms (keyword leg + cross-encoder) — two prompts run concurrently at temperature 0, cached per
   normalized query; the query as typed is embedded in parallel
 → Stage 1 candidate retrieval: scoped `hybrid_search` SQL function (V19), hard-filtered by Hub/Region.
   Three legs, each ranking ENTITIES by their best chunk or profile document: keywords (OR, IDF-weighted),
   embedding of the English query, embedding of the query as typed (off when already English). RRF-fused, a fixed
   `candidate-depth` (50) independent of matchCount
 → Stage 2 cross-encoder rerank: the reranker (cohere/rerank-5-fast) scores the top `rerank-depth` (30)
   candidates, each on its profile card (800 characters), drops those below `reranker-threshold` (0.3) —
   the only relevance gate
 → Soft re-rank: multiplicative boost `ceScore × (1 + W·availabilityOverlap + W·openToRequests)`
   — runs AFTER the cross-encoder so soft signals only fine-tune order, never decide who passes
 → Response: ranked results with their evidence chips, no rationale
 → Grounding, per result card on request (`POST /search/rationale`): the LLM writes a German
   rationale + validated citations from the same evidence
```

**The LLM never decides who gets in** — it only explains. If nothing clears the reranker threshold, `sufficient = false` and the frontend prompts the user to widen scope or create an Anfrage.

Scope resolution (`ScopeResolver`) is a **hard filter** here, walking Hub → Region → CH. Regression queries live in `../TechnicalImplementationDocs/Search_Regression_Queries.md`.

**Don't reword the query prompts casually.** The cross-encoder's score moves by an order of magnitude with small phrasing changes in the condensed query (a dropped "expert", missing commas), and `reranker-threshold` was calibrated on what the current prompts produce. Merging both prompts into one combined call lost up to 5 of 46 positive queries in evaluation. Any prompt change needs a re-run of the `evaluation-v3` harness. The query rewrite must stay deterministic: identical searches must return identical orderings. The query translation has its own prompt (`translateSearchQuery`), not the ingestion one: that one left half the German queries untranslated.

**Candidate retrieval (V19).** Stage 1 collapses to one row per entity *before* limiting — chunk-level limits let one researcher with 40 publication chunks fill every slot. Results are cut to `matchCount` only after reranking. Leg weights are `orbit.search.retrieval.*` (0 switches a leg off). The keyword leg gets the condensed terms minus role words ("expert", "researcher", …), so it never sees conversational filler. Pass vectors to native queries through a `WITH q AS MATERIALIZED (SELECT CAST(? AS vector) …)` CTE: a bound `CAST(? AS vector)` inside an inlined SQL function is re-parsed per row under a generic plan (116 ms vs 8 ms).

**Reranking.** There is no bi-encoder similarity gate: embedding similarities shift with query length, and the old 0.80 cut removed relevant candidates the cross-encoder never saw. The cross-encoder scores each candidate on its profile card — name, headline, tags, first summary sentences, start of the best chunk — cut to `orbit.search.rerank-passage-chars` (800). `orbit.search.rerank-passages` (0) can add evidence chunks with a `Name — Headline / Source type: label` header, the candidate then taking its best passage; with the previous reranker that found no more profiles than the card alone and more chunks let a negative control through (2026-11-16; not measured with the current reranker). `reranker-threshold` (0.3) is calibrated for cohere/rerank-5-fast on exactly the card-only input — changing the reranker, the passages, their length or the rerank query needs an `evaluation-v3` re-run, and a timing run against the hosted models.

**Search holds no transaction and no connection across model calls.** `SearchService.search` and `explain` are not `@Transactional`: a transaction would keep a database connection through the LLM, embedding and reranker calls and a few concurrent searches would drain the pool. A query without two letters or digits (`x`, `?`) returns nothing without calling a model. `SearchRateLimitFilter` caps the two public search endpoints per client address (`orbit.search.rate-limit.*`, 429 beyond).

**Lazy rationale.** `search()` never calls the LLM for a rationale. Each result card calls `POST /api/v1/search/rationale` (`{query, entityId}`, public) → `SearchService.explain`, which re-fetches the entity's evidence with the English query vector (cached from the search, so no new embedding) and grounds the rationale. Until it arrives the card shows the summary and every evidence chip; then only the cited chips.

**Search trace.** `POST /search` with `"debug": true` returns a `trace` (`SearchTrace`): every stage-1 candidate with its rank in each retrieval leg, similarity, reranker score, the passage it came from (`rerankedOn`) and the stage that dropped it (`rerank-depth`, `reranker`, `result-limit`), per-stage timings, and an entity-collapsed semantic ranking for comparison. It is admin-only unless `orbit.search.trace-open` (`ORBIT_SEARCH_TRACE_OPEN`) is true — open it for local evaluation only. A traced search also scores the candidates below the rerank depth so depth and threshold can be swept offline (timed separately as `trace-rerank`); the returned results do not change. The harness that consumes it is `../TechnicalImplementationDocs/evaluation-v3/`.

## Data model

- **Entity → Source → Chunk** is the core hierarchy. `DiscoveryEntity` carries display name, headline, org, LLM summary, tags, availability (open_to_requests, dates, pensum, collaboration types), affiliation, languages, location, hub memberships, tag subscriptions, profile mode, home region, `owner_id`.
- `Source` has source_type, label, URL, fetch status, provenance, consent_basis, and a `content_hash` that makes re-ingestion idempotent. The hash is taken over the adapter's `contentFingerprint()` — the **fetched** input, never the LLM-generated core text — and is checked before any LLM or embedding call. Leave volatile counters (stars, citation counts) out of a fingerprint. A changed source is updated in place, keeping its id and `active` flag.
- `Chunk` has content, `vector(1024)` embedding, FTS tsvector, section label, token count. Pass chunk text to `EmbeddingService` **without** a prefix — the service tells the model whether it is a query or a passage (`EmbeddingKind`), by `input_type` or by prefix, whichever the configured model uses. `Chunk.embedding` is a plain `float[]` that Hibernate can write but not read back; update vectors with a native query (`ChunkRepository.updateEmbedding`), never load + save.
- `profile_documents` (V19): one row per entity — name, headline, org, summary, tags — embedded and full-text indexed (English + German stemming). A **search index only**: `hybrid_search` retrieves with it, but evidence and citations come from sources, never from it. `ProfileDocumentService.refreshAfterCommit(entityId)` re-embeds it in the background after enrichment and after summary, tag or profile-meta edits; call it from any new code path that changes those fields. On start-up it embeds missing, changed or other-model documents (`orbit.search.profile-documents.sync-on-startup`).
- `ReembeddingService` runs in the background after every start-up and re-embeds chunks that have no vector or the vector of another embedding model than the configured one, 32 chunks per request — so a failed embedding is retried and a model change migrates itself. `orbit.embedding.reembed-before` (`ORBIT_EMBEDDING_REEMBED_BEFORE`, ISO-8601 instant) additionally re-embeds every chunk embedded before that instant, for a change the model name does not show (a new prefix). It changes vectors only; text that should be extracted anew needs a resync (see Ingestion).
- **Requests:** `Request` → `RequestTag` → `RequestDistribution`; `ContactRequest` for the first-contact handoff.
- **Tags** (`app.tag.TagVocabularyService`): every path that writes a tag — derived tags, profile tag edits, subscriptions, request tags — goes through `findOrMint`, which reuses an existing tag with the same spelling key (case, umlauts/ß, trailing `(ACRONYM)`, hyphens, plural ending) before minting by slug. Enrichment additionally maps new labels by meaning (`canonicalLabels`): existing tags at embedding similarity ≥ `orbit.tags.canonical-similarity` (0.78 for voyage-4-lite) are offered to the LLM, which must confirm the same topic — similarity alone pairs Genomik with Genetik. Tag vectors live in `tags.embedding` (V21) with the name of the model that made them. Existing tags are never merged automatically.
- 21 Flyway migrations (V1–V21) in `backend/src/main/resources/db/migration/`.

## Ingestion (supply side)

Ingestion runs as **background jobs** (V20 `ingestion_jobs`, package `app.ingestion.job`). `POST /api/v1/ingestion`, the two PDF uploads and `POST /entities/{id}/resync` only queue a job and answer `202` with an `IngestionJobDto`; the frontend polls `GET /api/v1/ingestion/jobs?ids=…` until `settled` (`useIngestionJobs` / `useSourceIngestion`). An uploaded PDF is stored on the job row until the job finishes, then dropped.

- `IngestionJobWorker` polls every second and runs jobs on `orbit.ingestion.jobs.workers` threads. `IngestionJobStore.claimNext` serializes claims with an advisory lock and enforces: **one job at a time per owner, in submission order** (the first source of an onboarding creates the entity the later ones join), at most `max-running-per-source-type` jobs per source type, and an **enrichment only once the owner has no source job left** — one onboarding is enriched once, not per source. A finished source arms (or pushes back) the entity's single queued `enrich` job by `enrich-delay`.
- Transient failures (provider outage, timeout, DB hiccup) are retried with backoff `retry-backoff × 4^(attempt-1)`, 3 attempts; `IllegalArgumentException`/`IllegalStateException` fail at once. A running job whose heartbeat stops for `stale-after` (the process died) is queued again.
- `IngestionOrchestrator.ingest` makes every network call — fetch, LLM core extraction, one `embedAll` batch per source — **before** a single short persist transaction. A failed embedding throws instead of storing a chunk without a vector; the job retries, and the dedup step skips what already went in. `EntityEnrichmentService.enrich` likewise calls the LLM first and writes in one short transaction. Don't put network calls inside a transaction.
- **A user's source never lands on another account's profile.** If its URL or an external id (LinkedIn URL, GitHub login, ORCID, Zefix UID, domain) already belongs to a profile with a different `owner_id`, `IngestionOrchestrator` throws `IngestionRejectedException` before any LLM call; the job fails without retry and `IngestionJobDto.message` carries the German reason the wizard shows. Unowned (harvested) profiles are still claimed. Before this, a second account's LinkedIn upload attached to the first account's profile, re-enriched it, and left the new account without a draft (2026-11-17).
- A GitHub account with no public repos is rejected with that reason, without calling GitHub's search (it answers 422 for some such accounts); an unknown login is rejected as not found.
- A GitHub repo with no README, description or topics yields no chunk: a summary of a bare name was a guess the cross-encoder scored above the threshold for unrelated queries.
- OpenAlex takes the 15 most-cited works plus the most recent ones (25 in all), and each publication chunk states the year and the author's position (`Published 2024 · last author`).
- **Re-evaluating stored profiles** after a pipeline change: `POST /api/v1/admin/reindex` (admin; optional body `{"entityIds": [...]}`, default every entity with a source; `GET` for progress) queues one `resync` job per entity (`ReindexService`). A resync re-fetches GitHub, OpenAlex, Zefix and website sources — a source whose fingerprint differs is extracted and embedded anew in place, an unchanged one is skipped — leaves uploads and manual text as they are, then enriches. Enrichment regenerates the summary, also one the owner edited by hand.

## Request lifecycle (demand side)

`open → distributed → contacted → closed`.

- **Distribution** (`DistributionService`) is **rule-based on tags + scope, not semantic** — deliberately, so "warum wurde ich benachrichtigt" stays exactly explainable. The cohort is entities whose `entity_tags` overlap the request tags (`profile_match`) ∪ entities subscribed to them (`subscription`), hard-filtered to the request's effective scope.
- **Escalation** (`EscalationScheduler`) widens the scope over time based on urgency; waves are idempotent via `UNIQUE(request_id, entity_id)`, so a wave only adds newly in-scope entities. Durations are ISO-8601 and shrinkable for demos (`ORBIT_REQUESTS_URGENT_ESCALATE_AFTER=PT2M`).
- **Contact** (`ContactService`) brokers exactly **one** message with a frozen sender context; everything after runs off-platform via the email shown in the recipient's Postfach. **No SMTP** — the handoff is in-app only.

## Auth — Clerk JWT

- Backend is an OAuth2 resource server validating Clerk session tokens on `/api/v1/**`, stateless, CORS limited to `clerk.azp-origin`.
- **Public routes** (no token) are whitelisted in `ClerkSecurityConfig`.
- `UserProvisioningFilter` runs after bearer auth and JIT-upserts the local `users` row.
- Admin access is a config-driven allowlist of Clerk `sub` claims: `orbit.admin.user-ids` / `ORBIT_ADMIN_USER_IDS`.
- Frontend: `ClerkProvider` wraps the app, `AuthProvider` syncs Clerk → Redux `auth` slice, RTK Query auto-attaches the bearer token.

---

## Style Guide — Backend (MUST FOLLOW)

**Group `com.orbit` · Spring Boot 4 · Java 21.** Packages: `domain.entity` / `domain.repository` (JPA), `app.controllers` (+ `dto/`), `app.profile`, `app.ingestion` (+ per-source subpackages), `app.search`, `app.request`, `app.contact`, `app.hub`, `app.llm`, `app.tag`, `app.embedding`, `app.inference`, `app.admin`, `app.auth`, `app.util`, `security`.

### Thin controllers — controllers do NO business logic

Controllers receive the request, delegate to a service in **one call**, and map the result to an HTTP response. That's it, plus nothing else.

**Controllers must NOT:** access repositories, build DTOs, contain if/else business logic (only null-checks for response mapping), or do owner/admin checks inline.

### Service pattern

Each domain area has one `@Service` owning its business logic — `EntityService`, `IngestionService`, `IngestionOrchestrator`, `EntityEnrichmentService`, `ChunkingService`, `SearchService`, `RerankService`, `RequestService`, `DistributionService`, `ContactService`, `InboxService`, `HubService`, `ProfileService`, `LlmSummaryService`.

`@Transactional` goes on **service methods**, never on controllers. Mutations are transactional; reads are not.

### Exception → HTTP status mapping

A single `@RestControllerAdvice` (`GlobalExceptionHandler` in `app.controllers`) does all mapping. **No controller has its own `@ExceptionHandler`.**

| Signal | HTTP status |
|---|---|
| `EntityAccessDeniedException` | 403 — entity exists, current user isn't the owner |
| `IllegalArgumentException` | 400 — invalid input |
| Service returns `null` | 404 — not found |

```java
// Service — throws for 403/400, returns null for 404
private DiscoveryEntity loadOwnedEntity(Long id) {
    DiscoveryEntity entity = entities.findById(id).orElse(null);
    if (entity == null) return null;                                // → 404
    if (!isOwner(entity)) throw new EntityAccessDeniedException();  // → 403
    return entity;
}

// Controller — just delegates
EntityDto dto = entityService.updateAvailability(id, ...);
return dto != null ? ResponseEntity.ok(dto) : ResponseEntity.notFound().build();
```

### JPA entity patterns

- **Plain `Long` FKs, never `@ManyToOne`/`@OneToMany`.** Resolve via repository calls. Avoids lazy-loading surprises and N+1. Example: `Hub.regionId` is a `Long`, not a `Region`.
- **`@IdClass` for composite keys** on join tables (`HubMembership`, `TagSubscription`, `EntityTag`, `RequestTag`, `EntityLink`) — one `@Id @Column` field per key column, matching `*Id` class.
- **PostgreSQL `text[]`** via `@JdbcTypeCode(SqlTypes.ARRAY)` + `columnDefinition = "text[]"` for simple string lists. JSONB (`SqlTypes.JSON`) only for nested structures.
- **`@Generated(event = EventType.INSERT)`** on `created_at`/`updated_at`. For `@IdClass` entities this conflicts in Hibernate 7.x — there, use `insertable = false, updatable = false` and let the DB `DEFAULT now()` handle it.
- **One JPA entity per file.** Value-object records go in a `dto/` subpackage.

### DTO patterns

- Java `record` types, in `com.orbit.app.controllers.dto`. Requests are `Update*Request`; responses are `*Dto`.
- **DTO assembly lives in the service.** `EntityService.toDto()` builds the full `EntityDto` with sources, tags, availability, hubs, region.
- **`EntityDto` is the read model** — it carries everything the frontend needs in one call. Don't add endpoints for sub-resources; extend the DTO and `toDto()`.

### Flyway migration patterns

- One migration per feature. `ALTER TABLE` for new columns, `CREATE TABLE` for new entities, seed data inline (`INSERT ... SELECT` for FK references).
- **Never modify a released migration** — always add a new one. File name: `V{n}__{description}.sql`.

---

## Style Guide — Frontend (MUST FOLLOW)

**React 19 · TypeScript · Vite 7 · Tailwind v4 · shadcn/ui (radix-nova, base neutral, lucide).** Path alias `@` → `./src`.

```
frontend/src/
├── components/   # Shared: ui/, layout/, branding/
├── features/     # auth, ingestion, onboarding, profile, search, requests, contact, inbox, admin
├── pages/        # Route-level components
├── routes/       # Router config
├── lib/          # store.ts, api.ts (RTK Query), hooks.ts, utils.ts, glossary.ts, clerkToken.ts
├── test/         # Shared fixtures (entityFixture.ts)
└── types/        # api.ts — all API DTO types
```

Tests are colocated (`Component.test.tsx`), vitest + jsdom + Testing Library, configured inside `vite.config.ts`.

### RTK Query

- **Single `apiSlice`** in `lib/api.ts` — every endpoint lives there.
- Tag types drive cache invalidation (`"MyEntities"`, `"Entity"`, `"Hubs"`, `"Subscriptions"`, …); mutations invalidate so the profile auto-refreshes.
- Types in `types/api.ts` **mirror the backend DTOs exactly**. Don't add types for endpoints that don't exist.
- `useLazy*Query` for on-demand (autocomplete, search), `use*Query` for auto-fetch. Export all hooks at the bottom of the file.

### Component patterns

- **`ProfileSectionCard`** wraps titled profile sections. **`SourceGlyph`** renders 2-char source badges. **`TagAutocompleteInput`** (debounced 200ms, ↑/↓/Enter/Esc) is the input for *any* tag field.
- Add shadcn primitives via `npx shadcn add <component>` — don't hand-roll one that shadcn already has.

### Mode-dependent rendering

- `useProfileMode()` reads the mode from the user's entity; the single source of truth is the persisted `entity.profileMode`.
- `ModeSwitch` in the header toggles "Ich suche" / "Ich biete". Nav is mode-dependent: demand → Suche + Meine Anfragen; supply → Anfrage-Board + Postfach. `OwnProfileView` hides the completeness banner, availability editor, and subscribed topics in demand mode.

### Onboarding wizard

State machine over a `WizardStep` union + `stepIndex`. Steps are conditional (orgs get a `firm` step, persons don't). Availability + subscriptions appear on the **post-ingestion** screen because they need an `entityId` that only exists after ingestion. `useRef` guards one-time effects.

### Styling

Tailwind v4 via `@tailwindcss/vite` — config lives in `src/index.css`, there is **no `tailwind.config.js`**. Prettier: no semicolons, double quotes, 2-space indent, `prettier-plugin-tailwindcss` sorts classes. Use `cn()`/`cva()` from `lib/utils.ts`.

---

## Key non-obvious rules

1. Controllers are thin HTTP adapters — no business logic, no repositories, no DTO building, no owner checks.
2. No JPA associations. Plain `Long` FKs + repository lookups.
3. Never modify a released Flyway migration; never let Hibernate alter tables (`ddl-auto: validate`).
4. `@Transactional` on services only.
5. Frontend types mirror backend DTOs — don't invent types ahead of the API.
6. Search is **evidence-forward, not score-forward**: every match cites sources; no percentage bar as the primary signal. Rationales are generated in **German**.
7. Scope (Hub → Region → CH) is a hard filter in search but a *widening* mechanism in request escalation — don't conflate the two.
8. Distribution is deliberately rule-based (tags), not semantic, so the notification reason stays exact.
9. DE default with EN toggle. The multilingual embedding model handles cross-language matching — never translate content just to make it match.

## Other notes

- `../Prototyping/` and `../TechnicalImplementationDocs/` are outside the build. `../TechnicalImplementationDocs/DiscoveryEnginePlan/` holds the design references the backend Javadoc cites by `§`.
- `.llm-wiki/`, `data/`, `claude-design/`, and `k8s/orbit.yml` + `k8s/deploy.sh` are gitignored.
