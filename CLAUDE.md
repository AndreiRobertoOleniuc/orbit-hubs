# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Orbit** (formerly *BadenConnect*) — semantic matchmaking platform for the Swiss innovation ecosystem (FHNW P5, Spring 2025; client: PowerPlant / Baden 4.0). Two surfaces on one data layer:

- **Inserat Board** — public, no-login, Ricardo-style posts (looking for / offering / open call), with tag subscription.
- **Discovery Engine** — free-text semantic search returning ranked people/companies with a plain-language rationale and cited evidence. Geographic scope (FHNW → Aargau → CH) is a re-ranking *weight*, not a hard filter.

Profiles are **generated on-demand** from linked sources (GitHub, ORCID, LINDAS/Handelsregister, …), never hand-authored. Primary demo: a founder posts a need → the engine surfaces experts/companies with rationale and sources.

> **Naming:** product renamed BadenConnect → Orbit. Java package is now `com.orbit` and the canonical name is Orbit, but the **Postgres database is still named `badenconnect`** and older docs/spec text say "BadenConnect". Same product.

**Canonical spec:** `docs/Orbit_Spec.md` (German). Planning docs in `docs/` (user stories MoSCoW, data model & API, aggregation logic, task breakdown, 5-week plan, ER diagram).

## Implementation status (read before assuming a feature exists)

The **persistence layer and auth are real**; most of the **application/API layer is still stubbed**.

- ✅ **Real:** JPA domain model + repositories, Flyway schema (V1–V4), Clerk JWT auth (resource server, JIT user provisioning), the `/api/v1/me` endpoint, and the full frontend scaffold (routing, Clerk→Redux sync, RTK Query client covering all *planned* endpoints).
- 🚧 **Stubbed:** `com.orbit.app.*` services/APIs are mostly empty interfaces or throw `UnsupportedOperationException` (e.g. `SearchService` is commented out, `ContactApi`/`OnboardingApi` throw). The only REST controller is `MeController`. So the endpoints the frontend's `apiSlice` calls (`/search`, `/board`, `/requests`, `/onboarding`, …) are **not implemented server-side yet** — the RTK Query surface is ahead of the backend.

## Commands

### Backend (`backend/`)

```bash
./mvnw spring-boot:run          # Start backend (http://localhost:8080)
./mvnw test                     # Run all tests
./mvnw test -Dtest=ClassName    # Run a single test class
./mvnw verify                   # Full build + tests
./mvnw package -DskipTests      # Build JAR
```

**Postgres must be started manually first** — `docker compose -f backend/compose.yaml up -d`. The Spring Boot docker-compose autostart dependency is **commented out** in `pom.xml`, so `spring-boot:run` will *not* launch the database for you. Flyway runs migrations on boot; Hibernate is `ddl-auto: validate` and never mutates the schema.

`compose.yaml` ships only `pgvector/pgvector:pg16` (port 5432, db `badenconnect`, user `root`, password `123456789`). **Ollama is not in compose** and there is no Ollama block in `application.yaml`; embedding/LLM features need an external Ollama or added config before they work.

### Frontend (`frontend/`)

```bash
npm run dev          # Vite dev server (http://localhost:5173)
npm run build        # tsc -b && vite build
npm run typecheck    # tsc --noEmit
npm run lint         # ESLint
npm run format       # Prettier write (no semicolons, double quotes)
npm run preview      # Preview production build
```

No frontend test runner is configured. Required env: `VITE_CLERK_PUBLISHABLE_KEY`; optional `VITE_API_BASE_URL` (default `http://localhost:8080`), `VITE_DEV_JWT` (local auth bypass — see below).

## Architecture

### Matching pipeline (target design)

```
Post / Query
  → Embedding retrieval    (multilingual-e5-large, 1024-dim, via Ollama / Spring AI)
  → Hybrid search          (Postgres FTS tsvector/GIN + pgvector HNSW cosine)
  → Reciprocal Rank Fusion (scope weights: FHNW → Aargau → CH)
  → LLM rationale          (top results + cited evidence → plain-language "why this match")
```

### Backend — Spring Boot (layered packages, **not** Modulith)

- **Group:** `com.orbit` · **Artifact:** `backend` · **Spring Boot 4.0.6 · Java 21 · Spring AI 2.0.0-M6 · Spring MVC.**
- There is **no Spring Modulith** dependency (a previous iteration used it; it's gone). The structure is plain layered packages under `com.orbit`:
  - `domain.entity` / `domain.repository` — JPA entities and Spring Data repositories (the implemented core).
  - `app.<feature>` — per-feature application layer (`discovery`, `embedding`, `request`, `profile`, `onboarding`, `contact`, `notification`, `auth`). The codebase keeps a **published-interface convention** here: a public `*Api`/`*Service` interface plus a package-private impl, so features depend on each other's interfaces rather than concrete beans. Most are still stubs.
  - `security` — Clerk OAuth2 resource-server config.
  - `controllers` — REST controllers (only `MeController` so far).
- **Persistence:** Spring Data JPA for entity lifecycle/CRUD. **`hibernate-vector`** maps Java `float[]` ↔ Postgres `vector(1024)` (no separate Spring Data JDBC layer for vectors).
- **Schema is Flyway-owned** (`src/main/resources/db/migration/V*.sql`). Changing an entity mapping requires a matching migration — never rely on Hibernate to alter tables. The Flyway Maven plugin in `pom.xml` has hardcoded local creds for the `badenconnect` db.

### Data model (key non-obvious shapes)

- **PKs are `text` slugs/external IDs, not UUIDs.** DTO ids are `String` to match.
- **`entities`** — the matchable thing (person *or* company, `type` column). Owns small fixed-cardinality children: `availability` (1:1 shared PK) and the `entity_tags` join to a shared `tags` vocabulary.
- **`documents` = the citable unit** (source/citation metadata only). **`document_chunks` = the retrieval unit** (text + `vector(1024)` embedding + `tsv` FTS column). This split (migration V4) means re-embedding one document never rewrites another. `entities.summary` is intentionally **not** embedded.
- **`requests`** — demand-side posts with their own `embedding` + `tsv`, a `status` lifecycle (`OPEN → DISTRIBUTED → CONTACTED → CLOSED`) audited in `request_transitions`, and `request_distributions` recording who was notified and why.
- `contact_handoffs` model one-time first-contact email hand-offs. `users.entity_id` ↔ `entities.owner_user_id` are mutual FKs (a user self-claims one entity; nullable until claimed).

### Auth — Clerk JWT (implemented)

- Backend is an **OAuth2 resource server** validating Clerk session tokens (`ClerkSecurityConfig`). Security applies only to `/api/v1/**`, stateless, CORS limited to the configured frontend origin (`clerk.azp-origin`). Principal claim is `sub`; no granted authorities are derived.
- **Public (token-free) routes** are explicitly whitelisted: `POST /search`, `GET /search/rationale/**`, `GET /board`, `GET /requests/**`, `GET /entities/**`. Everything else under `/api/v1` requires a bearer token.
- **`UserProvisioningFilter`** runs after bearer auth and just-in-time upserts the local `users` row from verified Clerk claims. Issuer URI and AZP origin are env-overridable (`CLERK_ISSUER_URI`, `CLERK_AZP_ORIGIN`).
- Frontend: `ClerkProvider` (`@clerk/react`) wraps the app; **`AuthProvider` syncs Clerk state into the Redux `auth` slice** so the rest of the app only reads `useAuth()`/the slice and never touches Clerk directly. `VITE_DEV_JWT`, if set, bypasses Clerk and injects a static token — the local-dev path for hitting authenticated endpoints.

### Frontend — React + Vite + Tailwind v4

- **React 19 · TypeScript · Vite 7.** Path alias `@` → `./src`.
- **State/data:** Redux Toolkit store (`lib/store.ts`) with an `auth` slice and **RTK Query `apiSlice` (`lib/api.ts`)** as the single API client. `apiSlice` already declares the full planned endpoint surface and auto-attaches the bearer token from the auth slice — treat it as the source of truth for the intended REST contract (mirrored by types in `src/types/api.ts`).
- **Routing:** `react-router-dom` v7 (`routes/index.tsx`). Public routes (`/search`, `/board`, `/profile/:entityId`) vs `AuthGuard`-wrapped routes (`/onboarding`, own `/profile`).
- **Tailwind CSS v4** via `@tailwindcss/vite` (not PostCSS). Config lives in `src/index.css` — there is **no `tailwind.config.js`**. **ShadCN UI** (`components.json`, style `radix-nova`, base `neutral`, icons `lucide`); add via `npx shadcn add <component>` → `src/components/ui`.
- Prettier: no semicolons, double quotes, 2-space indent; `prettier-plugin-tailwindcss` sorts classes. Treat `cn()`/`cva()` as Tailwind-aware.

## Other notes

- `prototyping/` is a scratch area outside the build — not wired to backend or frontend.
- `.llm-wiki/` is a generated knowledge wiki over this repo (reference, not a build target).
- A parent `../CLAUDE.md` describes the broader thesis workspace and an older ESCO/`ch.fhnw.badenconnect` prototype phase; where it conflicts with this file (package name, Modulith, docker-compose autostart), **this file is current**.

## LLM Wiki (project knowledge base)
A self-maintaining wiki lives at .llm-wiki/. Its operating manual is
.llm-wiki/WIKI_SCHEMA.md — read that first before reading or writing any wiki page.
- Before non-trivial work: grep/read .llm-wiki/wiki/ for relevant prior pages and recall them.
- After any meaningful task: proactively file a durable insight as a markdown page
  under the right .llm-wiki/wiki/ subfolder, per WIKI_SCHEMA.md, using [[wikilinks]].
  Don't wait to be asked.
- To capture a source (URL/PDF/notes): follow the raw/ + wiki/sources/ flow in WIKI_SCHEMA.md.
- Only do heavyweight ingestion or reorganization when I explicitly ask.
Operate the wiki by editing files directly with your normal tools.
