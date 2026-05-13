# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**BadenConnect** — a semantic matchmaking platform for the Swiss innovation ecosystem (FHNW P5, Spring 2025). Two surfaces on one data layer: an **Inserat Board** (public, no-login, Ricardo-style posts) and a **Discovery Engine** (free-text semantic search with plain-language rationales). Primary demo scenario: startup founder posts a need → engine surfaces experts/companies with cited evidence.

Partners: Andrei Oleniuc & Luca Nana. Client: PowerPlant / Baden 4.0 Initiative.

## Commands

### Frontend (`frontend/`)

```bash
npm run dev          # Vite dev server (http://localhost:5173)
npm run build        # tsc -b && vite build
npm run typecheck    # tsc --noEmit (no emit, just type errors)
npm run lint         # ESLint
npm run format       # Prettier write
npm run preview      # Preview production build
```

### Backend (`backend/`)

```bash
./mvnw spring-boot:run          # Start backend (auto-starts Docker Compose services)
./mvnw test                     # Run all tests
./mvnw test -Dtest=ClassName    # Run a single test class
./mvnw package -DskipTests      # Build JAR
./mvnw verify                   # Full build + tests
```

Backend requires Docker running — Spring Boot auto-launches `compose.yaml` (Ollama + pgvector) on startup.

## Architecture

### Matching Pipeline

```
Post / Query
    ↓
Embedding Retrieval  (multilingual-e5-large via Ollama)
    ↓
Hybrid Search  (BM25 + pgvector in PostgreSQL)
    ↓
Reciprocal Rank Fusion  (scope weights: FHNW → Aargau → CH)
    ↓
LLM Rationale Generation  (top results + source evidence → plain-language explanation)
```

### Backend — Spring Boot Modulith

- **Group ID:** `ch.fhnw.badenconnect`
- **Spring Boot 4.0.6 / Java 21 / Spring AI 2.0.0-M6 / Spring Modulith 2.0.6**
- Modular monolith: Spring Modulith enforces module boundaries. Cross-module access only via published interfaces, not direct bean injection. Each top-level package under `ch.fhnw.badenconnect.backend` is a module.
- **Spring Data JPA** for entity lifecycle / CRUD, **Spring Data JDBC** for raw pgvector queries (vector similarity search can't go through JPA).
- **Spring AI** wired to local Ollama for `multilingual-e5-large` embeddings and pgvector as the vector store.
- **Docker Compose** (`compose.yaml`): `pgvector/pgvector:pg16` (port 5432, Spring Boot service-connection auto-configures datasource) + `ollama/ollama:latest` (port 11434).
- Auth: Spring Security — JWT validation expected from Clerk (frontend handles OAuth flow for GitHub/LinkedIn social login).

### Frontend — React + Vite + Tailwind v4

- **React 19 / TypeScript / Vite 7**
- **Tailwind CSS v4** loaded via `@tailwindcss/vite` plugin (not PostCSS). Tailwind config lives in `src/index.css`, not `tailwind.config.js`.
- **ShadCN UI** (`components.json` configured). Add components: `npx shadcn add <component>`.
- Path alias `@` → `./src` (configured in `vite.config.ts` and `tsconfig.app.json`).
- Prettier: no semicolons, double quotes, 2-space indent, `prettier-plugin-tailwindcss` for class sorting. `cn()` and `cva()` are recognized Tailwind functions.

### Data Sources

| Source | What |
|---|---|
| LINDAS SPARQL → Handelsregister | Swiss company anchor layer (backbone for all discovery) |
| ORCID · OpenAlex · ROR | Researcher publications and affiliations |
| IRF FHNW (OAI-PMH) | FHNW institutional repository |
| GitHub · LinkedIn · personal sites | User-linked at registration (on-demand, opt-in) |

Profiles are **generated on-demand** from linked sources — no manual form filling.

### Build Order (Implementation Sequence)

1. **Inserat Primitive** — post/offer/open-call model with public read, tag subscription
2. **Handelsregister Entity Index** — LINDAS SPARQL ingestion into pgvector
3. **Researcher Profile Aggregation** — ORCID/OpenAlex/ROR/IRF harvester
4. **Semantic Matching** — embedding ingestion, hybrid BM25+pgvector search, RRF reranking
5. **Rationale Generator** — LLM call with top-ranked results + evidence sources
6. **Lightweight Onboarding** — Clerk OAuth, integration-link registration only
