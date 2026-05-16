# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**BadenConnect** — semantic matchmaking platform for the Swiss innovation ecosystem (FHNW P5, Spring 2025; client: PowerPlant / Baden 4.0). Two surfaces on one data layer:

- **Inserat Board** — public, no-login, Ricardo-style posts (looking for / offering / open call).
- **Discovery Engine** — free-text semantic search returning ranked people/companies with a plain-language rationale and cited evidence. Geographic scope (FHNW → Aargau → CH) is a re-ranking weight, not a hard filter.

Primary demo scenario: startup founder posts a need → engine surfaces experts/companies with rationale and sources.

The repo is in early scaffolding — backend is a bare `@SpringBootApplication`, frontend is the Vite/React/Tailwind skeleton plus a theme provider. Treat README.md as the product spec and build order.

## Commands

### Frontend (`frontend/`)

```bash
npm run dev          # Vite dev server (http://localhost:5173)
npm run build        # tsc -b && vite build
npm run typecheck    # tsc --noEmit
npm run lint         # ESLint
npm run format       # Prettier write
npm run preview      # Preview production build
```

### Backend (`backend/`)

```bash
./mvnw spring-boot:run          # Start backend (auto-launches compose.yaml services)
./mvnw test                     # Run all tests
./mvnw test -Dtest=ClassName    # Run a single test class
./mvnw package -DskipTests      # Build JAR
./mvnw verify                   # Full build + tests
```

Backend requires Docker running — `spring-boot-docker-compose` auto-starts `compose.yaml` on `spring-boot:run` and wires the datasource via Spring Boot service connections (no manual `application.yaml` JDBC config needed).

## Architecture

### Matching Pipeline (target design — not yet implemented)

```
Post / Query
    ↓
Embedding Retrieval  (multilingual-e5-large via Ollama / Spring AI)
    ↓
Hybrid Search  (BM25 + pgvector in PostgreSQL)
    ↓
Reciprocal Rank Fusion  (scope weights: FHNW → Aargau → CH)
    ↓
LLM Rationale Generation  (top results + source evidence → plain-language explanation)
```

### Backend — Spring Boot Modulith

- **Group ID:** `ch.fhnw.badenconnect` · **Artifact:** `backend`
- **Spring Boot 4.0.6 · Java 21 · Spring AI 2.0.0-M6 · Spring Modulith 2.0.6**
- Modular monolith: Spring Modulith enforces module boundaries — each top-level package under `ch.fhnw.badenconnect.backend` is a module, and cross-module access must go through published interfaces, not direct bean injection.
- **Spring Data JPA** for entity lifecycle / CRUD; **Spring Data JDBC** for raw pgvector similarity queries (vector ops don't fit JPA's model).
- **Spring AI** is wired for Ollama (`spring-ai-starter-model-ollama`) and the pgvector vector store (`spring-ai-starter-vector-store-pgvector`).
- **Spring Security** is on the classpath but no auth config exists yet. README plans Clerk JWTs (frontend OAuth for GitHub/LinkedIn) — verify wiring before assuming it's there.
- **`compose.yaml` currently ships only `pgvector/pgvector:pg16`** (port 5432, db `badenconnect`, user `root`). Ollama is referenced in the pom and README pipeline but is **not** in compose — running embedding/LLM features requires adding it or pointing Spring AI at an external Ollama.

### Frontend — React + Vite + Tailwind v4

- **React 19 · TypeScript · Vite 7**
- **Tailwind CSS v4** loaded via `@tailwindcss/vite` plugin (not PostCSS). Tailwind config lives in `src/index.css` — there is no `tailwind.config.js`.
- **ShadCN UI** (`components.json`, style `radix-nova`, base color `neutral`, icons `lucide`). Add components via `npx shadcn add <component>` — they land in `src/components/ui`.
- Path alias `@` → `./src` (configured in `vite.config.ts` and `tsconfig.app.json`).
- Prettier: no semicolons, double quotes, 2-space indent; `prettier-plugin-tailwindcss` sorts classes. Treat `cn()` and `cva()` as Tailwind-aware functions.

### Data Sources (per README; ingestion not yet built)

| Source | What |
|---|---|
| LINDAS SPARQL → Handelsregister | Swiss company anchor layer (backbone for all discovery) |
| ORCID · OpenAlex · ROR | Researcher publications and affiliations |
| IRF FHNW (OAI-PMH) | FHNW institutional repository |
| GitHub · LinkedIn · personal sites | User-linked at registration (on-demand, opt-in) |

Profiles are **generated on-demand** from linked sources — no manual form filling.

### Build Order (Implementation Sequence)

1. **Inserat Primitive** — post/offer/open-call model with public read, tag subscription.
2. **Handelsregister Entity Index** — LINDAS SPARQL ingestion into pgvector.
3. **Researcher Profile Aggregation** — ORCID/OpenAlex/ROR/IRF harvester.
4. **Semantic Matching** — embedding ingestion, hybrid BM25+pgvector, RRF reranking with scope weights.
5. **Rationale Generator** — LLM call with top-ranked results + evidence sources.
6. **Lightweight Onboarding** — Clerk OAuth, integration-link registration only.

## Other Notes

- `prototyping/` is a scratch area outside the build — not wired to backend or frontend.
- A parent `CLAUDE.md` exists at `../CLAUDE.md` for the older ESCO-matching prototype phase of this project; the ESCO CSV dataset it describes is not currently used by this repo.
