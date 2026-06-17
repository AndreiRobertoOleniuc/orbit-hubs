# Orbit — Reading List

> Curated docs to read **away from the keyboard** to build a better mental model of how Orbit
> is constructed. Grouped to mirror the stack: frontend state, backend modules/DDD, the vector
> layer, and the matching algorithm. See `KNOWLEDGE.md` for the project's own architecture dump.

---

## 📁 Redux / Frontend State

The split that matters: **client state** (you own it — auth token, theme, UI) lives in a **slice**;
**server state** (a cached copy of what the backend owns) lives in **RTK Query**. RTK Query is built
on top of `createAsyncThunk`, so it generates the `pending`/`fulfilled`/`rejected` pattern for you,
plus caching and invalidation.

- **RTK Query — Overview & motivation** — why this instead of hand-written thunks.
  → https://redux-toolkit.js.org/rtk-query/overview
- **RTK Query — Comparison with other tools** — when to use it vs not.
  → https://redux-toolkit.js.org/rtk-query/comparison
- **Redux Essentials, Part 7 — RTK Query Basics** ⭐ (do this one for real, with `frontend/src/lib/api.ts` open)
  → https://redux.js.org/tutorials/essentials/part-7-rtk-query-basics
- **Redux Essentials, Part 8 — RTK Query Advanced** (caching, tags/invalidation)
  → https://redux.js.org/tutorials/essentials/part-8-rtk-query-advanced
- **RTK Query usage — Queries / Mutations**
  → https://redux-toolkit.js.org/rtk-query/usage/queries
  → https://redux-toolkit.js.org/rtk-query/usage/mutations
- **TypeScript with RTK Query** — typed hooks, `RootState`/`AppDispatch` (matches `frontend/src/lib/hooks.ts`)
  → https://redux-toolkit.js.org/usage/usage-with-typescript
- **`createAsyncThunk` reference** — the pending/fulfilled/rejected pattern; still the right tool for
  bespoke async that isn't data fetching.
  → https://redux-toolkit.js.org/api/createAsyncThunk
- **`createSlice` reference** — for client-state slices like `authSlice.ts`.
  → https://redux-toolkit.js.org/api/createSlice

---

## 📁 Spring Modulith

- **Reference docs (home)** → https://docs.spring.io/spring-modulith/reference/
- **Fundamentals** — application modules, allowed dependencies, OPEN vs closed (maps to KNOWLEDGE §4:
  `auth` and `embedding` are OPEN). → https://docs.spring.io/spring-modulith/reference/fundamentals.html
- **Verification** — the `ModularityTests` boundary test.
  → https://docs.spring.io/spring-modulith/reference/verification.html
- **Documentation** — PlantUML / C4 generation from module structure.
  → https://docs.spring.io/spring-modulith/reference/documentation.html
- **Oliver Drotbohm talks** (Modulith lead) — search "Oliver Drotbohm Spring Modulith" (SpringOne / Devoxx)
  for the "why a modular monolith" narrative.

---

## 📁 DDD / Spring Data JDBC

Spring Data JDBC is built **around DDD aggregates**, not an ORM: no lazy loading, no dirty tracking —
one repository per **aggregate root**, loads/saves the whole aggregate.

- **Jens Schauder — "Spring Data JDBC, References, and Aggregates"** ⭐ — explains `@MappedCollection`
  (inside an aggregate) vs cross-aggregate references, and why Orbit uses plain `String` IDs across
  aggregates instead of `AggregateReference` (KNOWLEDGE §5).
  → https://spring.io/blog/2018/09/24/spring-data-jdbc-references-and-aggregates
- **Spring Data JDBC — reference docs** → https://docs.spring.io/spring-data/relational/reference/jdbc.html
- **Spring Data JDBC — entity persistence / aggregates**
  → https://docs.spring.io/spring-data/relational/reference/jdbc/entity-persistence.html
- **Spring Framework — `JdbcClient`** — the read side: `JdbcClient` + immutable DTO records, never
  `CrudRepository` for search/vectors (KNOWLEDGE §5).
  → https://docs.spring.io/spring-framework/reference/data-access/jdbc/core.html
- **Vaughn Vernon — "Effective Aggregate Design" (free, 3-part)** — the theory under all of the above.
  **Part 2** ("reference other aggregates by identity") is why `Distribution` is its own root and why
  cross-aggregate refs are `String` IDs.
  → Part 1: https://www.dddcommunity.org/wp-content/uploads/files/pdf_articles/Vernon_2011_1.pdf
  → Part 2: https://www.dddcommunity.org/wp-content/uploads/files/pdf_articles/Vernon_2011_2.pdf
  → Part 3: https://www.dddcommunity.org/wp-content/uploads/files/pdf_articles/Vernon_2011_3.pdf
  → Mirror (if the above is down): https://kalele.io/effective-aggregate-design/

---

## 📁 Vector (embeddings + storage)

- **pgvector README** — the actual vector store: HNSW vs IVFFlat, and the `<=>` cosine / `<->` L2 /
  `<#>` inner-product operators. → https://github.com/pgvector/pgvector
- **`intfloat/multilingual-e5-large` model card** — the model in the prototype (KNOWLEDGE §3). Read it
  for the **prefix requirement** (see Gotcha #1). → https://huggingface.co/intfloat/multilingual-e5-large
- **Vicki Boykis — "What Are Embeddings"** (free book) — best from-scratch grounding on what an
  embedding *is*. → https://vickiboykis.com/what_are_embeddings/
- **Spring AI — Embeddings API + pgvector Vector Store** — how this is wired on the backend.
  → https://docs.spring.io/spring-ai/reference/api/embeddings.html
  → https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html
- **OpenAI — Embeddings guide** — for the 5-week cloud plan (`text-embedding-3-small`, KNOWLEDGE §11).
  Mind the **1024 vs 1536 dimension** mismatch against the `vector(1024)` columns.
  → https://platform.openai.com/docs/guides/embeddings
- **Pinecone Learn** (vendor-neutral education) — HNSW and similarity-metric explainers.
  → https://www.pinecone.io/learn/

---

## 📁 Matching / Retrieval (the algorithm)

Maps 1:1 onto KNOWLEDGE §8: embed → scope hard-filter → hybrid retrieve → RRF → weight re-rank →
rollup → lazy rationale.

- **Supabase — Hybrid Search** ⭐ — Postgres `tsvector` + pgvector combined with **RRF**, with full SQL.
  Almost a line-for-line blueprint of steps 3–4. Start here.
  → https://supabase.com/docs/guides/ai/hybrid-search
- **"Building Hybrid Search for RAG: pgvector + FTS + RRF"** (DEV) — the same thing as a narrative
  walkthrough with the CTE pattern.
  → https://dev.to/lpossamai/building-hybrid-search-for-rag-combining-pgvector-and-full-text-search-with-reciprocal-rank-fusion-6nk
- **PostgreSQL — Full-Text Search** (official) — the lexical half: `tsvector`, `tsquery`, `ts_rank`,
  GIN indexes (KNOWLEDGE §6). → https://www.postgresql.org/docs/current/textsearch.html
- **Elastic — "Practical BM25"** — what BM25 actually computes and why (see Gotcha #2).
  → https://www.elastic.co/blog/practical-bm25-part-2-the-bm25-algorithm-and-its-variables
- **Cormack et al. (2009) — Reciprocal Rank Fusion** (original paper, ~2 pages) — RRF is just
  `1/(k+rank)`, k≈60. → https://dl.acm.org/doi/pdf/10.1145/1571941.1572114
- **Re-ranking** (optional/later) — Orbit's step 5 is a *weight multiply* by `availability × presence_mode`,
  not a cross-encoder. Know the general technique exists: Cohere Rerank.
  → https://docs.cohere.com/docs/rerank-overview

---

## 📁 System Design (small, Orbit-shaped)

- **Martin Fowler — CQRS** — names what Orbit already does (aggregates for writes; `JdbcClient` +
  read-model DTOs for search/reads, KNOWLEDGE §5). → https://martinfowler.com/bliki/CQRS.html
- **Martin Kleppmann — "Designing Data-Intensive Applications"** (book) — the one systems-design book
  worth the time; Ch. 3 (storage/indexes) and the derived/search-data sections map onto Orbit. (Not free.)
- **microservices.io — Saga / async messaging** — small dose, for the async **Distribution** step
  (KNOWLEDGE §13 open Q2: distribution synchronous vs scheduled job?).
  → https://microservices.io/patterns/data/saga.html

---

## ⚠️ Gotchas to remember (not yet in KNOWLEDGE.md)

1. **e5 needs asymmetric prefixes.** `multilingual-e5-large` was trained so you must prepend
   `"query: "` to search queries and `"passage: "` to indexed documents. Embed both sides raw and
   relevance quietly tanks — it *looks* like it works but ranks poorly. The most common e5 mistake.
2. **Postgres native FTS ≠ BM25.** CLAUDE.md's pipeline says "BM25 + pgvector," but Postgres
   `ts_rank`/`ts_rank_cd` is *not* BM25 (no IDF saturation, no proper length normalization). Fine for
   the MVP — RRF over rank position hides most of the gap. For *true* BM25 in Postgres, that's the
   **ParadeDB `pg_search`** extension → https://github.com/paradedb/paradedb

> Connecting thread, frontend ↔ backend: RTK Query draws a hard line around **server vs client state**;
> Spring Data JDBC + Modulith draw a hard line around **aggregate boundaries**. Both force you to be
> explicit about *who owns this data and who may reach into it*. And in ranking, always ask: **is this a
> filter or a weight?** Scope is a hard filter; availability/presence are multiplicative weights (KNOWLEDGE §8).
