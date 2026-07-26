# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> `AGENTS.md` is a byte-identical mirror of this file (except the header) for other agents.
> **When you change this file, apply the same change to `AGENTS.md`.**

## Project Overview

**Orbit** — semantic matchmaking platform for the Swiss innovation ecosystem (FHNW P5; client: PowerPlant / Baden 4.0). Asymmetric demand/supply platform:

- **Supply side** — profiles auto-generated from linked sources (GitHub, OpenAlex, LinkedIn PDF, Zefix, websites, manual text). Users set availability ("Offen für Anfragen"), subscribe to topics, join hubs.
- **Demand side** — free-text semantic search returning ranked people/companies with a German rationale and cited evidence. When search finds nothing sufficient, the seeker creates an **Anfrage** (request) that lands on the public board and is pushed to matching profiles.

**Canonical spec:** `docs/Specs/Orbit_Specv2.md`. The `§` references in Javadoc throughout the backend point at `docs/DiscoveryEnginePlan/orbit-discovery-engine-reference.md`.

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
npm run typecheck                 # tsc --noEmit
npm run lint                      # ESLint
npm run format                    # Prettier write (no semicolons, double quotes)
```

Required env: `VITE_CLERK_PUBLISHABLE_KEY`; optional `VITE_API_BASE_URL` (default `http://localhost:8080`).

### Reranker microservice (`reranker/`) — optional, local only

```bash
pip install -r reranker/requirements.txt
python reranker/server.py          # -> http://localhost:8001
```

FastAPI wrapper around `BAAI/bge-reranker-v2-m3`. Only needed when `RERANKER_PROVIDER=local`; the default is `huggingface` (hosted inference), so you normally do **not** run this.

### Deploy (`k8s/`)

```bash
VITE_CLERK_PUBLISHABLE_KEY='pk_...' ./k8s/deploy.sh all   # or: backend | frontend
```

Builds multi-arch images, pushes to Docker Hub, applies `k8s/orbit.yml` to k3s. There is no CI pipeline in the repo — deployment is driven from a laptop.

## Inference providers — read before touching search or embeddings

Embeddings and the cross-encoder are **provider-swappable** and both default to **Hugging Face hosted inference**, not local models:

| Concern | Property | Default | Alternative |
|---|---|---|---|
| Embeddings (1024-dim `multilingual-e5-large`) | `orbit.embedding.provider` | `huggingface` | `ollama` (needs local Ollama on 11434) |
| Cross-encoder rerank | `orbit.search.reranker-provider` | `huggingface` | `local` (needs `reranker/server.py` on 8001) |
| Chat / rationale | Spring AI `google-genai` | `gemini-2.5-flash-lite` | — |

`HF_TOKEN` and `GEMINI_API_KEY` are required for a fully working search. Search degrades gracefully to evidence-only cards without the LLM. All tunables live in `backend/src/main/resources/application.yaml` and are env-overridable.

## The search pipeline (`SearchService`)

```
Query
 → LLM prep: full English translation (retrieval) + condensed search-style query (cross-encoder)
 → Stage 1 bi-encoder retrieval: scoped `hybrid_search` SQL function (FTS + pgvector fused by RRF),
   hard-filtered by Hub/Region, then similarity-threshold filtered
 → Stage 2 cross-encoder rerank: BAAI/bge-reranker-v2-m3 scores (query, doc) pairs, drops irrelevant
 → Soft re-rank: multiplicative boost `ceScore × (1 + W·availabilityOverlap + W·openToRequests)`
   — runs AFTER the cross-encoder so soft signals only fine-tune order, never decide who passes
 → Grounding: Gemini generates a German rationale + validated citations for the top N
```

**The LLM never decides who gets in** — it only explains. If nothing clears the thresholds, `sufficient = false` and the frontend prompts the user to widen scope or create an Anfrage.

Scope resolution (`ScopeResolver`) is a **hard filter** here, walking Hub → Region → CH. Regression queries live in `docs/Search_Regression_Queries.md`.

## Data model

- **Entity → Source → Chunk** is the core hierarchy. `DiscoveryEntity` carries display name, headline, org, LLM summary, tags, availability (open_to_requests, dates, pensum, collaboration types), affiliation, languages, location, hub memberships, tag subscriptions, profile mode, home region, `owner_id`.
- `Source` has source_type, label, URL, fetch status, provenance, consent_basis, and a `content_hash` that makes re-ingestion idempotent.
- `Chunk` has content, `vector(1024)` embedding, FTS tsvector, section label, token count.
- **Requests:** `Request` → `RequestTag` → `RequestDistribution`; `ContactRequest` for the first-contact handoff.
- 18 Flyway migrations (V1–V18) in `backend/src/main/resources/db/migration/`.

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

**Group `com.orbit` · Spring Boot 4 · Java 21.** Packages: `domain.entity` / `domain.repository` (JPA), `app.controllers` (+ `dto/`), `app.profile`, `app.ingestion` (+ per-source subpackages), `app.search`, `app.request`, `app.contact`, `app.hub`, `app.llm`, `app.embedding`, `app.inference`, `app.admin`, `app.auth`, `app.util`, `security`.

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

- `prototyping/` and `docs/` are outside the build. `docs/DiscoveryEnginePlan/` holds the design references the backend Javadoc cites by `§`.
- `.llm-wiki/`, `data/`, `claude-design/`, and `k8s/orbit.yml` + `k8s/deploy.sh` are gitignored.
