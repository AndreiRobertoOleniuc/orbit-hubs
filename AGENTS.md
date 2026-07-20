# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Project Overview

**Orbit** — semantic matchmaking platform for the Swiss innovation ecosystem (FHNW P5; client: PowerPlant / Baden 4.0). Asymmetric demand/supply platform:

- **Supply side** — profiles auto-generated from linked sources (GitHub, ORCID, LinkedIn PDF, Zefix, websites). Users set availability ("Offen für Anfragen"), subscribe to topics, and join hubs.
- **Demand side** — free-text semantic search returning ranked people/companies with cited evidence. Anfrage lifecycle (Offen→Verteilt→Kontaktiert→Geschlossen), matching, distribution, Postfach inbox.

**Canonical spec:** `docs/Orbit_Spec.md` (v2 is current).

## Implementation status (updated July 13, 2026)

The **backend is substantially built** — not stubbed. The **frontend supply side is 100% complete**; the demand-side **search is fully functional**, but the Anfrage lifecycle (board, requests, inbox, contact) is still placeholders.

### Backend — fully functional
- **10 Flyway migrations** (V1–V10): users, discovery engine core (entities/sources/chunks), hybrid search function, entity ownership, tags+summary, supply availability, hubs/regions, tag subscriptions, profile mode, scoped hybrid search.
- **7 ingestion adapters** all working: GitHub, OpenAlex, LinkedIn PDF, Zefix, Manual Text, Personal Website, Company Website.
- **5-stage search pipeline**: (1) bi-encoder retrieval (FTS + pgvector + RRF, scoped), (2) soft re-rank (availability overlap + open-boost), (3) cross-encoder reranking (BAAI/bge-reranker-v2-m3 via Python microservice, condensed query + truncated summary, no single-candidate bypass), (4) LLM grounding (Gemini Flash, German rationale + validated citations), (5) render. Two LLM calls produce a full English translation (for retrieval) and a condensed search-style query (for cross-encoder). Rationale is generated in German. Validated at 96% pass rate across 28 test queries.
- **Entity enrichment**: LLM competence summary + AI-derived tags after each ingestion.
- **Supply-side profile**: availability, affiliation, languages, location, hub membership, tag subscriptions, profile mode.
- **Admin hub management**: config-driven admin allowlist, CRUD for hubs/regions.
- **Test artifacts to remove**: `BulkIngestController`, `BulkIngestService`, `BulkIngestRequest`, and the security-config `permitAll` lines for `/api/v1/test/bulk-ingest`.

### Frontend — supply side complete, search functional, Anfrage lifecycle pending
- **Supply side**: onboarding wizard (type→firm→sources→ingestion→availability+subscriptions→profile preview), profile page (completeness banner, availability editor, hub selector, subscribed topics, kompetenzen, sources, nachweise), profile mode switching (Ich suche / Ich biete), profile meta editor.
- **Demand side — search**: fully functional. `SearchPage` → `SearchView` with `SearchForm` (query, scope ladder, Zeitfilter, Pensum) → `SearchResults` → `SearchResultCard` (entity, rationale, citation chips, availability badge, distance, "Profil ansehen" button). No "Kontakt aufnehmen" button yet.
- **Demand side — Anfrage lifecycle**: routes and navigation structure complete (`/board`, `/requests`, `/inbox`) but all three pages are 14-line `PlaceholderPage` stubs. No Anfrage entity, migration, service, or controller exists in the backend.
- **Admin**: hub management page at `/admin/hubs`.

## Commands

### Backend (`backend/`)

```bash
./mvnw spring-boot:run          # Start backend (http://localhost:8080)
./mvnw test                     # Run all tests
./mvnw compile                  # Compile only
./mvnw test-compile             # Compile tests
./mvnw verify                   # Full build + tests
```

**Postgres must be started manually first** — `docker compose -f backend/compose.yaml up -d`. Flyway runs migrations on boot; Hibernate is `ddl-auto: validate` and never mutates the schema.

**Ollama is not in compose** — embedding/LLM features need an external Ollama serving `multilingual-e5-large` (1024 dims).

### Frontend (`frontend/`)

```bash
npm run dev          # Vite dev server (http://localhost:5173)
npm run build        # tsc -b && vite build
npm run typecheck    # tsc --noEmit
npm run lint         # ESLint
npm run format       # Prettier write (no semicolons, double quotes)
```

Required env: `VITE_CLERK_PUBLISHABLE_KEY`; optional `VITE_API_BASE_URL` (default `http://localhost:8080`).

---

## Style Guide — Backend (MUST FOLLOW)

### Thin controllers — controllers do NO business logic

**Controllers are thin HTTP adapters.** They receive the request, delegate to a service, and map the result to an HTTP response. **All business logic lives in service classes.**

**Controller responsibilities (exhaustive):**
1. Receive HTTP request (path variables, request body)
2. Delegate to the service (one method call)
3. Map service result to HTTP response (ok / notFound / forbidden / badRequest)
4. `@ExceptionHandler` for service-thrown exceptions

**Controller must NOT:**
- Access repositories directly
- Build DTOs (the service assembles DTOs)
- Contain if/else business logic (only null-checks for response mapping)
- Do owner/admin checks inline (the service does these)

### Service pattern

Each domain area has a `@Service` class that owns all business logic:

| Service | Package | Owns |
|---------|---------|------|
| `EntityService` | `com.orbit.app.profile` | Entity reads, resync, tags/summary/availability/meta/hubs/mode/subscriptions, owner checking, DTO assembly |
| `IngestionService` | `com.orbit.app.ingestion` | URL building, provenance mapping, LinkedIn PDF lifecycle, result aggregation |
| `HubService` | `com.orbit.app.hub` | Hub CRUD, admin authorization, region listing |
| `SearchService` | `com.orbit.app.search` | Hybrid search execution |
| `IngestionOrchestrator` | `com.orbit.app.ingestion` | 6-step ingestion pipeline (fetch→dedup→resolve→upsert→chunk→embed) |
| `EntityEnrichmentService` | `com.orbit.app.ingestion` | Post-ingestion LLM summary + tag derivation |
| `LlmSummaryService` | `com.orbit.app.llm` | All Gemini LLM calls: `translateToEnglish()` (full query translation for retrieval), `translateAndCondenseSearchQuery()` (condensed query for cross-encoder), `summarizeGithubRepo()`, `summarizeWebsite()`, `generateMatchRationale()` |
| `ChunkingService` | `com.orbit.app.ingestion` | Token-based chunk splitting |
| `ProfileService` | `com.orbit.app.profile` | JIT user provisioning |

### Exception → HTTP status mapping

A single `@RestControllerAdvice` (`GlobalExceptionHandler` in `com.orbit.app.controllers`) maps service-thrown exceptions to HTTP status codes globally. **No controller has its own `@ExceptionHandler`.**

| Exception | HTTP status | When |
|-----------|-------------|------|
| `EntityAccessDeniedException` | 403 Forbidden | Entity exists but current user is not its owner |
| `IllegalArgumentException` | 400 Bad Request | Invalid input (e.g. unknown mode, invalid regionId) |
| Service returns `null` | 404 Not Found | Entity not found |

**Pattern:**
```java
// Service — throws for 403/400, returns null for 404
private DiscoveryEntity loadOwnedEntity(Long id) {
    DiscoveryEntity entity = entities.findById(id).orElse(null);
    if (entity == null) return null;              // → 404
    if (!isOwner(entity)) throw new EntityAccessDeniedException();  // → 403
    return entity;
}

// Controller — just delegates, no exception handling
EntityDto dto = entityService.updateAvailability(id, ...);
return dto != null ? ResponseEntity.ok(dto) : ResponseEntity.notFound().build();

// GlobalExceptionHandler — one place for all exception → status mapping
@ExceptionHandler(EntityAccessDeniedException.class)
public ResponseEntity<Void> handleAccessDenied(EntityAccessDeniedException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
}
```

### Transaction boundaries

`@Transactional` goes on **service methods**, never on controllers. Mutation methods (create, update, delete, replace) are `@Transactional`. Read methods are not.

### JPA entity patterns

- **Plain `Long` FKs**, not `@ManyToOne`/`@OneToMany` associations. Resolve via repository calls (`repo.findById()`). This avoids Hibernate lazy-loading issues and N+1 queries. Example: `Hub.regionId` is `Long`, not `Region region`.
- **`@IdClass` for composite keys** on join tables: `HubMembership`, `TagSubscription`, `EntityTag`. Pattern:
  ```java
  @Entity @Table(name = "hub_memberships")
  @IdClass(HubMembershipId.class)
  public class HubMembership {
      @Id @Column(name = "entity_id") private Long entityId;
      @Id @Column(name = "hub_id") private Long hubId;
      // ...
  }
  ```
- **PostgreSQL `text[]` arrays** mapped via `@JdbcTypeCode(SqlTypes.ARRAY)` with `columnDefinition = "text[]"` for simple string lists (`preferredCollaboration`, `languages`). Use JSONB (`@JdbcTypeCode(SqlTypes.JSON)`) only for nested structures.
- **`@Generated(event = EventType.INSERT)`** on `created_at`/`updated_at` — avoids `@IdClass` + `@Generated` conflicts in Hibernate 7.x. For `@IdClass` entities (join tables), use `insertable = false, updatable = false` on timestamp columns instead and let the DB `DEFAULT now()` handle it.
- **No multi-type-per-file** — one JPA entity per file. Value-object records go in a `dto/` subpackage.

### DTO patterns

- **Java `record` types** for all DTOs — immutable, concise. Located in `com.orbit.app.controllers.dto`.
- **Request DTOs**: `Update*Request` (e.g. `UpdateAvailabilityRequest`, `UpdateTagsRequest`).
- **Response DTOs**: `*Dto` (e.g. `EntityDto`, `HubDto`, `AvailabilityDto`).
- **DTO assembly lives in the service**, not the controller. `EntityService.toDto()` builds the full `EntityDto` with all nested data (sources, tags, availability, hubs, region).
- **EntityDto is the read model** — it carries everything the frontend needs in one call. Don't add new endpoints for sub-resources; extend the DTO and the `toDto()` method.

### Repository patterns

- Spring Data JPA interfaces in `com.orbit.domain.repository`.
- Query methods: `findByEntityId`, `findByOwnerIdOrderByCreatedAtDesc`, `findBySlug`, `findAllByOrderByNameAsc`.
- Autocomplete: `findTop10ByLabelContainingIgnoreCaseOrderByLabelAsc(query)`.

### Flyway migration patterns

- **One migration per feature** (V6=availability, V7=hubs/regions, V8=subscriptions, V9=mode).
- **ALTER TABLE for new columns** on existing tables. CREATE TABLE for new entities.
- **Seed data** goes in the migration (INSERT INTO ... SELECT ... for FK references).
- **Never modify a released migration** — always add a new one.
- Migration files: `src/main/resources/db/migration/V{n}__{description}.sql`.

### Security patterns

- **Public routes** whitelisted in `ClerkSecurityConfig`: `POST /search`, `GET /entities/**`, `GET /hubs`, `GET /board`, `GET /requests/**`, `GET /search/rationale/**`.
- **Owner checks** in services via `loadOwnedEntity()` — not in controllers.
- **Admin checks** via `AdminAccess.isAdmin(userId)` — config-driven allowlist (`orbit.admin.user-ids` / `ORBIT_ADMIN_USER_IDS`).
- **CurrentUser** abstraction (`com.orbit.app.auth.CurrentUser`) — injected into services, never accessed from controllers.

---

## Style Guide — Frontend (MUST FOLLOW)

### Feature-based structure

```
frontend/src/
├── components/          # Shared components (ui/, layout/, branding/)
├── features/            # Feature modules (one dir per feature)
│   ├── auth/             # AuthGuard, AuthProvider, authSlice, useAuth
│   ├── ingestion/        # SourceCard, sourceDefs, types, requests, useSourceIngestion
│   ├── onboarding/       # OnboardingWizard, steps/, IngestionRunView, ProfilePreview
│   ├── profile/          # OwnProfileView, PublicProfileView, ProfileHeader, AvailabilityEditor, etc.
│   └── admin/            # HubAdminView
├── pages/               # Route-level page components
├── routes/              # Router config (index.tsx)
├── lib/                 # store.ts, api.ts (RTK Query), utils.ts
└── types/               # api.ts (all API DTO types)
```

### RTK Query patterns

- **Single `apiSlice`** in `lib/api.ts` — all endpoints in one file.
- **Tag types** for cache invalidation: `"MyEntities"`, `"Entity"`, `"Hubs"`, `"Subscriptions"`.
- **Mutations invalidate tags** so the profile auto-refreshes after edits.
- **Types in `types/api.ts`** — mirror the backend DTOs exactly. Don't add types for endpoints that don't exist yet.
- **`useLazy*Query`** for on-demand queries (autocomplete, search). **`use*Query`** for auto-fetching.
- Export all hooks at the bottom: `export const { useGetMeQuery, useUpdateAvailabilityMutation, ... }`

### Component patterns

- **`ProfileSectionCard`** for titled sections in the profile — consistent styling wrapper.
- **`SourceGlyph`** for 2-char source badges (GH, iD, in, 🌐, HR).
- **`TagAutocompleteInput`** — reusable autocomplete over `GET /tags?q=`. Debounced 200ms, keyboard nav (↑/↓/Enter/Esc). Use for any tag input (kompetenzen, subscriptions).
- **shadcn/ui components** in `components/ui/` — add via `npx shadcn add <component>`. Don't create custom primitives when shadcn has one.
- **`PlaceholderPage`** for not-yet-implemented pages — keeps navigation walkable.

### Onboarding wizard patterns

- **State machine** with `WizardStep` union type and `stepIndex`.
- **Steps are conditional**: orgs get `["type", "firm", "sources"]`, persons get `["type", "sources"]`.
- **Post-ingestion completion screen** (`IngestionRunView`) shows availability + subscriptions for supply onboarding — because these need an `entityId` which only exists after ingestion.
- **`useRef` guard** for one-time effects (e.g. setting demand mode after onboarding fork).

### Mode-dependent rendering

- **`useProfileMode()` hook** — reads mode from user's entity via `useGetMyEntitiesQuery`. Single source of truth is `entity.profileMode` (persisted).
- **`ModeSwitch` component** in AppLayout header — "Ich suche" / "Ich biete" toggle.
- **NavLinks is mode-dependent**: demand shows Suche + Meine Anfragen, supply shows Anfrage-Board + Postfach.
- **OwnProfileView conditional rendering**: demand hides completeness banner, availability editor, subscribed topics.

### Styling

- **Tailwind CSS v4** via `@tailwindcss/vite` (not PostCSS). Config in `src/index.css` — no `tailwind.config.js`.
- **Prettier**: no semicolons, double quotes, 2-space indent. `prettier-plugin-tailwindcss` sorts classes.
- **`cn()`/`cva()`** from `lib/utils.ts` for conditional class merging.

---

## Architecture

### Backend — Spring Boot (layered packages)

- **Group:** `com.orbit` · **Spring Boot 4.0.6 · Java 21**
- Package structure:
  - `domain.entity` / `domain.repository` — JPA entities and Spring Data repositories
  - `app.controllers` — thin REST controllers + `dto/` subpackage
  - `app.controllers.dto` — all DTO records
  - `app.profile` — EntityService, ProfileService, EntityAccessDeniedException
  - `app.ingestion` — IngestionOrchestrator, IngestionService, adapters, ChunkingService, EntityEnrichmentService
  - `app.search` — SearchService, SearchController
  - `app.hub` — HubService
  - `app.llm` — LlmSummaryService (5 Gemini methods)
  - `app.embedding` — EmbeddingService, OllamaEmbeddingService
  - `app.admin` — AdminAccess
  - `app.util` — SlugUtils, HashUtils, JsonUtils, HubDtos
  - `security` — ClerkSecurityConfig, ClerkJwtDecoderConfig, UserProvisioningFilter

### Data model (three-tier hierarchy)

- **Entity** → **Source** → **Chunk** — the core hierarchy.
- Entity has: display name, headline, org name, summary (LLM), tags (derived/manual), availability (open_to_requests, dates, pensum, collaboration types), affiliation, languages, location, hub memberships, tag subscriptions, profile mode, home region, owner_id.
- Source has: source_type, label, URL, fetch status, fetched_at, provenance, consent_basis, content_hash (idempotent re-ingestion).
- Chunk has: content, embedding (vector(1024)), FTS tsvector, section label, token count.

### Auth — Clerk JWT

- Backend is an **OAuth2 resource server** validating Clerk session tokens.
- Security applies to `/api/v1/**`, stateless, CORS limited to `clerk.azp-origin`.
- **Public routes** (no token): `POST /search`, `GET /entities/**`, `GET /hubs`, `GET /board`, `GET /requests/**`, `GET /search/rationale/**`.
- `UserProvisioningFilter` runs after bearer auth and JIT-upserts the local `users` row.
- Frontend: `ClerkProvider` wraps app, `AuthProvider` syncs Clerk → Redux `auth` slice, RTK Query auto-attaches bearer token.

### Frontend — React + Vite + Tailwind v4

- **React 19 · TypeScript · Vite 7.** Path alias `@` → `./src`.
- **State:** Redux Toolkit store (`lib/store.ts`) with `auth` slice + RTK Query `apiSlice` (`lib/api.ts`).
- **Routing:** `react-router-dom` v7. Public: `/`, `/search`, `/board`, `/profile/:entityId`. Auth-guarded: `/onboarding`, `/profile`, `/requests`, `/inbox`, `/admin/hubs`.
- **Tailwind v4** via `@tailwindcss/vite`. Config in `src/index.css`.
- **ShadCN UI** (`components.json`, style `radix-nova`, base `neutral`, icons `lucide`).

## Key non-obvious rules

1. **Never put business logic in controllers.** Controllers are thin HTTP adapters. All logic in services.
2. **Never access repositories from controllers.** Inject services, not repositories.
3. **Never build DTOs in controllers.** The service assembles and returns DTOs.
4. **Never use JPA associations (`@ManyToOne`, `@OneToMany`).** Use plain `Long` FKs and resolve via repository calls.
5. **Never modify a released Flyway migration.** Add a new one.
6. **Never rely on Hibernate to alter tables.** `ddl-auto: validate` — schema is Flyway-owned.
7. **One JPA entity per file.** Value-object records go in `dto/` subpackages.
8. **Frontend types mirror backend DTOs.** Don't add types for endpoints that don't exist yet.
9. **`@Transactional` on service methods, never on controllers.**
10. **Owner checks in services, not controllers.** Throw `EntityAccessDeniedException` for 403.

## Other notes

- `prototyping/` is a scratch area outside the build.