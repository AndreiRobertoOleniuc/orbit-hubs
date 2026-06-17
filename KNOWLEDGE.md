# Orbit — Project Knowledge Wiki Dump

> Auto-generated from the project LLM Wiki. Citations are `[[wiki/page]]` links to the underlying wiki pages.

---

## 1. What Orbit Is

Orbit is a **regional semantic matchmaking platform** for the Swiss innovation ecosystem around Baden, Brugg and FHNW. It sits between LinkedIn, an HR tool and a job board without being any of them. The core problem it solves: *"I need someone nearby whom I don't know exists."* [[sources/SRC-2026-06-17-006]]

The platform is **asymmetric**:
- **Demand acts**: a user searches, then (if needed) escalates to a public *Anfrage*.
- **Supply waits**: profiles are auto-generated from public sources and are findable 24/7 without hand-authoring.

There are two surfaces on one data layer:
- **Discovery Engine** — free-text semantic search returning ranked people/companies/*Anfragen* with a plain-language rationale and cited evidence.
- **Anfrage-Board** — public, login-free feed of open requests, filterable by tag and scope, sortable, tag-subscribable.

The first contact is a **one-time email hand-off**; there is no on-platform chat or inbox. [[sources/SRC-2026-06-17-006]]

---

## 2. Actors & Scenarios

Two actor types (Person / Organisation), but the meaningful split is **demand vs. supply**, both of which any actor can take. Both types are equal; the difference is only the sources used to build the profile and verification. [[sources/SRC-2026-06-17-001]]

Three primary demo scenarios:
1. **Startup ↔ Expert** — founder needs embedded-firmware help for two months.
2. **Researcher → Industry partner** — researcher with a paper needs an SME to co-write an Innosuisse proposal.
3. **Industry → Expert under time pressure** — project manager needs a multimodal-mobility specialist for two months.

Each scenario is designed to work on both a populated and an empty platform. [[sources/SRC-2026-06-17-006]]

---

## 3. Tech Stack

### Backend
- **Spring Boot 4.0.6**, **Java 21**, **Spring Modulith 2.0.6**
- **Spring Data JDBC** (JPA removed; switched to JDBC-only so tests pass) [[sources/obs-2026-06-16-backend-switched-to-jdbc-only-and-tests-now-pass]]
- **PostgreSQL 16 + pgvector** for vectors and FTS
- **Spring AI** with cloud AI provider (OpenAI recommended for the 5-week plan)
- **Clerk** JWT auth on the frontend, verified by Spring Security resource server [[sources/obs-2026-06-16-clerk-jwt-auth-jit-provisioning-implemented-in-orbit-backend]]

### Frontend
- **React 19 + TypeScript + Vite 7**
- **Tailwind CSS v4** (config in `src/index.css`, no `tailwind.config.js`)
- **ShadCN UI** already scaffolded
- **React Router** for routing
- **Redux Toolkit + react-redux** for global state
- **Clerk React** for auth, abstracted behind a Redux-backed token provider [[sources/orbit-auth-token-provider-redux-abstraction]]

### Deployment target (5-week plan)
- **k3s on a Raspberry Pi with 20 GB RAM**
- Cloudflare Tunnel or public IP + port forwarding
- Postgres as a StatefulSet in k3s; AI entirely cloud-hosted
- GitHub Actions → Docker / GHCR → k3s rolling update [[sources/SRC-2026-06-17-005]]

---

## 4. Spring Modulith Modules

The backend is a modular monolith. Each top-level package under `com.orbit` is a module. Cross-module access must go through **published interfaces**, not direct bean injection. [[sources/obs-2026-06-16-spring-modulith-module-skeleton-created-for-orbit]]

| Module | Type | Owns |
|---|---|---|
| `profile` | aggregate module | `Entity` (+ `availability`, `entity_tags`); `Document` (+ `document_chunks`) |
| `request` | aggregate module | `Request` (+ `request_transitions`); `Distribution` (own root) |
| `discovery` | read-only | no tables; native SQL read models / search |
| `embedding` | stateless service | no tables; `embed(text, kind) -> vector(1024)` |
| `onboarding` | stateless pipeline | writes into `profile` via its API |
| `contact` | stateless service | one-time email hand-off log |
| `notification` | stateless fan-out | optional outbox only |
| `auth` | open support module | publishes `CurrentUser`; no aggregate |

`auth` and `embedding` are **OPEN** modules; the rest are **closed** by default. A `ModularityTests` regression test enforces boundaries and generates PlantUML docs. [[sources/obs-2026-06-16-spring-modulith-boundary-test-added-for-orbit]] [[sources/obs-2026-06-16-spring-modulith-modules-reverted-to-encapsulated-default]]

---

## 5. Aggregate & Persistence Conventions

Critical rules for generating persistence code:

- **IDs are `String` slugs** for all domain aggregates (`per-marc-hofer`, `req-lisa-embedded`, `gh-mhofer-oauth21-migration`). **Do not use UUID.** The only `Long` ID is `request_distributions.id` (bigserial). [[sources/obs-2026-06-17-orbit-v2-id-type-decision-string-text-pks-in-java-aggregates]]
- **Aggregate class names match table names**: `Entity`, `Document`, `Request`, `Distribution`. Do **not** name the supply aggregate `Profile`. DTOs may still be `ProfileDto` / `RequestDto`. [[sources/obs-2026-06-17-orbit-aggregate-naming-entity-root-matches-entities-table]]
- **Inside an aggregate** → full child objects via `@MappedCollection`.
- **Across aggregates** → plain `String` id field (e.g. `ownerEntityId`). Do **not** use `AggregateReference`. [[sources/obs-2026-06-17-orbit-cross-aggregate-references-plain-string-ids-no-aggrega]]
- **Read side / search / vectors** → `JdbcClient` + immutable DTO records; never `CrudRepository` returning aggregate roots.
- **Vector dimension** is `vector(1024)` everywhere. [[sources/SRC-2026-06-17-002]]

### Document chunking
`Document` is its own aggregate root; `document_chunks` are `@MappedCollection` children. The embedding vector lives on the **chunk row**, not the document row, so re-embedding one document never rewrites others. Request text is short and stays embedded whole on `requests.embedding`. [[sources/SRC-2026-06-17-003]]

### Distribution is its own aggregate
`request_distributions` has an independent lifecycle (queried per recipient, drives notifications) and is therefore a separate aggregate root in the `request` module, not a child of `Request`. [[sources/SRC-2026-06-17-002]]

### Auth module subpackage visibility gotcha
When splitting a flat `internal` package into concern-based sub-packages, classes and constructors that were package-private must become `public` if referenced across sub-packages. `@Component` classes injected by reflection can stay package-private, but making them public avoids surprises. [[sources/spring-modulith-internal-subpackage-visibility]]

---

## 6. Database Schema (v2)

Flyway migration `V1__orbit_v2_schema.sql` creates 13 tables in the local compose Postgres (`localhost:5432/badenconnect`, user `root`, password `123456789`). [[sources/obs-2026-06-16-orbit-v2-flyway-schema-migration-created]]

Core tables:
- `users` — auth account; mutual FK with `entities` via `owner_user_id` / `entity_id`.
- `entities` — person or company profile; scope `fhnw|aargau|ch` acts as **hard filter**; `verified` badge for orgs.
- `documents` — citable source unit (GitHub repo, website, publication, etc.).
- `document_chunks` — embedded slices; `vector(1024)` + per-chunk `tsv`.
- `availability` — 1:1 with entity; "Offen für Anfragen" toggle + structured fields.
- `requests` — the *Anfrage*; status `OPEN|DISTRIBUTED|CONTACTED|CLOSED`; embedded whole.
- `request_transitions` — lifecycle audit log.
- `request_distributions` — who was notified and why.
- `contact_handoffs` — one-time first-contact log.
- `tags`, `entity_tags`, `request_tags`, `tag_subscriptions`, `source_connections`.

Indexes: HNSW cosine on vector columns, GIN on `tsv` columns, btree filters. [[sources/SRC-2026-06-17-003]]

A later `V4__document_chunks.sql` will move `embedding`/`tsv` off `documents` and backfill one chunk per existing document. [[sources/SRC-2026-06-17-002]]

Schema migration tip: for mutually referencing `users`/`entities`, create both tables first, then add cross-FKs with `ALTER TABLE`. [[sources/orbit-v2-schema-migration]]

---

## 7. Anfrage Lifecycle

Explicit four-state machine; every transition is logged.

```
OPEN ──▶ DISTRIBUTED ──▶ CONTACTED ──▶ CLOSED
  │           │              │
  └───────────┴──────────────┘ (creator can CLOSE from any live state)
```

| State | Meaning | Allowed next |
|---|---|---|
| `OPEN` | live on the board | `DISTRIBUTED`, `CLOSED` |
| `DISTRIBUTED` | matching profiles + subscribers notified | `CONTACTED`, `CLOSED` |
| `CONTACTED` | ≥1 contact hand-off brokered | `CLOSED` |
| `CLOSED` | solved; removed from board | terminal |

Backend invariants: reject illegal transitions with `409`; only owner may `CLOSED`; `DISTRIBUTED` is automatic or stays `OPEN` so demand never dead-ends. [[sources/SRC-2026-06-17-003]]

---

## 8. REST API Contract (Base: `/api/v1`)

JSON, JWT Bearer auth. Public endpoints need no token. [[sources/SRC-2026-06-17-003]]

### Public endpoints
- `POST /api/v1/search` — semantic search; scope is hard filter; returns ranked results with citations.
- `GET /api/v1/search/rationale/{entityId}?query=...` — lazy rationale + cited evidence.
- `GET /api/v1/board?tag=&scope=&sort=newest|relevant&page=` — public feed of `OPEN`/`DISTRIBUTED` requests.
- `GET /api/v1/requests/{id}` — request detail.
- `GET /api/v1/entities/{id}` — public profile + documents + availability.

### Auth endpoints
- `POST /api/v1/requests` — create *Anfrage* → `OPEN`.
- `PATCH /api/v1/requests/{id}/status` — owner-only lifecycle transition.
- `GET /api/v1/requests/{id}/distributions` — owner-only distribution list.
- `PATCH /api/v1/entities/{id}` — owner edit tags/description.
- `PUT /api/v1/entities/{id}/availability` — owner toggle + structured fields.
- `GET /api/v1/entities/{id}/matching-requests` — owner sees open matching requests.
- `POST /api/v1/onboarding/person` — link sources → ingest.
- `POST /api/v1/onboarding/organisation` — Zefix match → `verified=true`.
- `GET /api/v1/onboarding/{entityId}/status` — source sync progress.
- `POST /api/v1/contact` — one-time email hand-off.
- `POST /api/v1/subscriptions` / `DELETE /api/v1/subscriptions/{tagSlug}` — tag subscriptions.

### Discovery pipeline
1. Embed query (e5-large / OpenAI) → `vector(1024)`.
2. **Scope hard filter** (`fhnw ⊆ aargau ⊆ ch`; `all` = no filter).
3. Hybrid retrieve over `document_chunks` + `requests` (pgvector dense + FTS lexical).
4. Reciprocal Rank Fusion.
5. Re-rank by `availability.is_open` × `presence_mode` match (weights, **never filters**).
6. Rollup: chunk → document → entity.
7. Lazy rationale (LLM) on expand with cited sources.

Scope is a **hard filter**; availability/presence are **multiplicative weights**. [[sources/SRC-2026-06-17-003]]

---

## 9. User Stories (MoSCoW)

Quality criteria: QC-1 relevance ≥70%, QC-2 explainability ≥80%, QC-3 onboarding <10 min, QC-4 ≥3 scenarios. [[sources/SRC-2026-06-17-001]]

### Must
- **US-SRCH-1** — free-text semantic search without login.
- **US-SRCH-2** — scope chips hard-filter results.
- **US-SRCH-3** — every result has a plain-language rationale with cited evidence.
- **US-SRCH-4** — distance label on each result.
- **US-REQ-1** — escalate no-hit search into a public *Anfrage* (login only here).
- **US-REQ-2** — four-stage lifecycle.
- **US-REQ-3** — tag distribution with visible reason.
- **US-BOARD-1..4** — public board, filter/sort, detail view, tag subscription.
- **US-PROF-1..4** — auto-generated cited profile, manual edit, availability toggle, matching requests on profile.
- **US-ONB-1..2** — person/organisation onboarding.
- **US-CONTACT-1** — one-time email hand-off.
- **US-NOTIF-1** — notify matching profiles and tag-subscribers on distribution.

### Should / Could / Won't
Should: mixed result types, profile rationale parity, originator-type badge, notification centre.
Could: saved searches, profile completeness hint, *Anfrage* expiry.
Won't: active "Bieten" posts, recommendation chains, intelligent routing, LinkedIn company import, Google Scholar match, active "Verbundene Kreise" scope. [[sources/SRC-2026-06-17-001]]

---

## 10. Task Breakdown Status

Foundation cards (EPIC-0) that are Done / in progress:
- **T-FND-DATA-1** — v2 schema migration created. [[sources/obs-2026-06-16-orbit-v2-flyway-schema-migration-created]]
- **T-FND-BE-1** — Spring Modulith skeleton + boundary test created. [[sources/obs-2026-06-16-spring-modulith-module-skeleton-created-for-orbit]] [[sources/obs-2026-06-16-spring-modulith-boundary-test-added-for-orbit]]
- **T-FND-BE-3** — Clerk JWT auth + JIT provisioning implemented. [[sources/obs-2026-06-16-clerk-jwt-auth-jit-provisioning-implemented-in-orbit-backend]]
- **T-FND-FE-1** — frontend app shell implemented with routing, layout, Redux, API client, auth abstraction. [[sources/obs-2026-06-17-frontend-app-shell-t-fnd-fe-1-implemented]]

Remaining foundation: T-FND-DATA-2 (seed script), T-FND-BE-2 (Ollama/Spring AI embedding bean), T-FND-INT-1 (CI). The full Must backlog is ~34 dev-days across two devs. [[sources/SRC-2026-06-17-004]]

---

## 11. 5-Week MVP Plan (Cloud AI + k3s)

A compressed plan that swaps self-hosted AI/auth for SaaS to hit ≤5 weeks. [[sources/SRC-2026-06-17-005]]

### Substitutions
- Auth: **Clerk** (frontend SDK + JWT backend).
- Embeddings: **OpenAI `text-embedding-3-small`** (or HuggingFace Inference API for e5).
- Rationale LLM: **OpenAI `gpt-4o-mini`** with structured outputs.
- Email: **Resend** or SendGrid.
- Object storage: **Backblaze B2** or MinIO sidecar.
- CI/CD: **GitHub Actions** → GHCR/Docker Hub.
- DB: **Postgres + pgvector in k3s StatefulSet** on the Pi.

### Week-by-week milestones
- **Week 1** — schema, seeds, Modulith, Clerk auth, cloud AI beans, Docker/k3s scaffold, first deploy.
- **Week 2** — public search + rationale on the Pi.
- **Week 3** — profiles + onboarding.
- **Week 4** — *Anfrage* lifecycle + board + contact hand-off.
- **Week 5** — integration, polish, deploy hardening, QC acceptance.

### Deferred out of 5-week scope
All Should/Could/Won't stories; ORCID/Google Scholar for persons; LinkedIn company import; recommendation chains; full FADP audit. [[sources/SRC-2026-06-17-005]]

---

## 12. Key Decisions & Gotchas

1. **Backend switched to JDBC-only.** Removed `spring-boot-starter-data-jpa` and `spring-modulith-starter-jpa`; kept `spring-boot-starter-data-jdbc` + `spring-modulith-starter-jdbc`. Tests now pass against the local pgvector container. [[sources/obs-2026-06-16-backend-switched-to-jdbc-only-and-tests-now-pass]]
2. **Runtime Docker Compose removed.** User starts Postgres manually; `spring-boot-docker-compose` dependencies are commented out so the backend starts without compose. [[sources/obs-2026-06-16-removed-runtime-docker-compose-dependencies-from-backend]]
3. **Flyway enabled by default.** The explicit `spring.flyway.enabled: true` line was removed as redundant in Spring Boot 4; migration still runs because Flyway is on the classpath. [[sources/obs-2026-06-16-removed-redundant-spring-flyway-enabled-from-application-yam]]
4. **Frontend auth is Redux-abstracted.** Clerk React syncs into the Redux `auth` slice; `useAuth()`, route guards, and API clients read from the slice. A future auth-provider swap only touches `AuthProvider`. [[sources/orbit-auth-token-provider-redux-abstraction]]
5. **ShadCN UI eslint override.** `eslint.config.js` disables `react-refresh/only-export-components` for generated `src/components/ui/**/*` files. [[sources/obs-2026-06-17-frontend-app-shell-t-fnd-fe-1-implemented]]
6. **Aggregate boundaries confirmed.** `profile` owns `Entity` and `Document`; `request` owns `Request` and `Distribution`; `discovery` and `embedding` own no tables. [[sources/obs-2026-06-17-orbit-module-aggregate-boundaries-confirmed]]

---

## 13. Open Questions

From the API contract:
1. Auth provider decision is now resolved (Clerk chosen).
2. Distribution trigger: synchronous on create vs. scheduled job?
3. Distance computation: straight-line vs. routing API? MVP default = straight-line.
4. Ollama in compose: not present; cloud AI chosen for the 5-week plan. [[sources/SRC-2026-06-17-003]]

---

## 14. File Locations

- Spec / user stories: `docs/01_user_stories_moscow.md`, `docs/02_data_model_and_api.md`, `docs/02.1-aggregation-logic.md`, `docs/03_task_breakdown.md`, `docs/05_zero_to_orbit_5week_plan.md`, `spec/Orbit_Spec.md`
- Backend: `backend/src/main/java/com/orbit`
- Frontend: `frontend/src` (shell under `src/routes`, `src/features`, `src/components/layout`, `src/lib`)
- Wiki: `.llm-wiki/wiki/`

---

*Generated from the Orbit LLM Wiki. For the most current state, run `wiki_recall` / `wiki_search` and read the linked source pages.*
