# BadenConnect — v10 Discovery CLI Specification

**Version:** v10
**Status:** Prototype specification (supersedes v8/v9)
**Scope:** A command-line tool that demonstrates the full BadenConnect discovery engine — two-stage hybrid retrieval over people, companies, and inserate, with cited, evidence-anchored rationales.
**Target stack after the CLI proves out:** React (TypeScript) frontend + Spring Boot modulith + PostgreSQL/pgvector.

---

## 1. What BadenConnect is

BadenConnect solves one concrete problem, framed by the project coaches Norbert and Nitish: **find the person or company nearby that you don't know exists.** It is the digital version of walking into the office next door. Today, discovery in the Baden / Aargau innovation ecosystem runs on _Vitamin B_ — asking co-founders, posting in Slack groups, hoping the right person is at the next networking event. The expertise is often 200 meters away, and nobody connects the dots.

The platform is deliberately **not** LinkedIn, **not** an HR funnel, and **not** a job board. It sits in between: a lightweight bulletin board plus a discovery engine that explains every match with cited sources.

### The two surfaces, one data layer

**Surface 1 — Inserat Board (Ricardo-style).** A public feed of "looking for" (`suche`), "offering" (`biete`), and "open call" posts. Readable without login. Tag-subscribable. Posting takes under two minutes.

**Surface 2 — Discovery Engine.** Free-text semantic search ("what are you looking for?"). Scope widens FHNW → Aargau → Switzerland. Returns ranked people and companies with a text rationale and source evidence. **No percentage match scores as the primary signal** — every result explains _why_ with cited sources instead.

### The primary scenario this CLI must demonstrate

> Lisa, founder of a 6-person Baden IoT startup, needs short-term embedded firmware help — maybe two weeks. She is certain the expertise exists nearby but has no way to find it. On BadenConnect she posts an inserat in two minutes; it is public immediately. In parallel, the engine surfaces 3–5 candidates with rationales grounded in real evidence ("his GitHub shows three repos on Cortex-M power profiling"). She contacts them directly. No HR funnel.

The CLI is "ready" when a query like _"embedded firmware security review zwei wochen"_ returns a sensible mix of people, a company, and inserate, each with cited evidence — see [Section 9](#9-definition-of-done--when-the-cli-is-ready).

---

## 2. Architecture overview

The CLI is a vertical slice of the eventual modulith. It exercises every load-bearing part of the system except the web/UI layer, so that the later Spring Boot + React work becomes plumbing rather than discovery of unknowns.

```
External sources ──▶ Ingestion ──▶ [ documents / inserate in Postgres+pgvector ]
                                            │
User free-text query ──▶ Stage 1 retrieval (dense + lexical, RRF, instant)
                                            │
                          Stage 2 re-rank (entity rollup + scope weights)
                                            │
                          Render ranked list NOW (no LLM)
                                            │
                          Stage 3 rationale (lazy, on click → Gemini Flash)
```

Two ideas carry the whole design:

1. **Entities + documents split.** An _entity_ is a thing you match (person or company). A _document_ is one piece of evidence about an entity (a GitHub repo, a CV section, a website page). One entity has many documents. You retrieve documents, then roll them up to entities — so a person with five strong sources counts as one person, but you keep the individual document IDs to cite.

2. **Inserate are first-class, not entities.** An inserat has its own structured fields (kind, honorar mode, validity date) that a board needs to filter on, so it lives in its own table — but it also carries an embedding, so it participates in the same search.

---

## 3. Data model

Nine tables. The core is `entities` + `documents` + `inserate`; everything else is a thin structured layer the UI demands.

### Core tables

All Tables should be prepended with prefix `v10_` to allow to compare to previous implementations without conflicts. For brevity, the prefix is omitted in the descriptions below.

**`entities`** — the things you match.

| column                          | type        | notes                                                              |
| ------------------------------- | ----------- | ------------------------------------------------------------------ |
| `id`                            | text PK     | stable slug: `per-marc-hofer`, `firm-trafosoft`                    |
| `type`                          | text        | `person` \| `company`                                              |
| `name`                          | text        | display name                                                       |
| `headline`                      | text        | "Senior Security Engineer · Helvetia"                              |
| `location`                      | text        | free text: "Baden", "Brugg"                                        |
| `location_lat` / `location_lng` | double      | for the later map view                                             |
| `scope`                         | text        | `fhnw` \| `aargau` \| `ch` — a re-rank weight, **not** a filter    |
| `summary`                       | text        | LLM-synthesized card text; **not embedded**, not retrieved against |
| `handelsregister_uid`           | text        | `CHE-114.234.221` — the LINDAS anchor for companies                |
| `owner_user_id`                 | text FK     | nullable; set when a user claims this profile                      |
| `created_at` / `updated_at`     | timestamptz |                                                                    |

**`documents`** — the evidence you retrieve and cite.

| column         | type         | notes                                                                                             |
| -------------- | ------------ | ------------------------------------------------------------------------------------------------- |
| `id`           | text PK      | stable: `gh-mhofer-oauth21-migration`, `cv-marc-experience`                                       |
| `entity_id`    | text FK      | → `entities.id`                                                                                   |
| `source_type`  | text         | `github_repo` \| `website_page` \| `linkedin_cv` \| `publication` \| `github_bio` \| `orcid_work` |
| `source_url`   | text         | original URL or uploaded-file reference                                                           |
| `source_label` | text         | "GitHub · oauth21-migration" — the footnote label shown in the UI                                 |
| `text`         | text         | cleaned content, ≤ ~4000 chars                                                                    |
| `embedding`    | vector(1024) | multilingual-e5-large, `passage:` prefix                                                          |
| `tsv`          | tsvector     | `GENERATED ALWAYS AS` — lexical search index                                                      |
| `text_hash`    | text         | skip re-embedding if unchanged                                                                    |
| `meta`         | jsonb        | source-specific: `{stars, language, published_at}`                                                |
| `fetched_at`   | timestamptz  |                                                                                                   |

**`inserate`** — the board posts; first-class and searchable.

| column                                       | type         | notes                                           |
| -------------------------------------------- | ------------ | ----------------------------------------------- |
| `id`                                         | text PK      | `ins-marc-secrev`                               |
| `kind`                                       | text         | `suche` \| `biete` \| `open_call`               |
| `title` / `description`                      | text         | title min 6 chars                               |
| `owner_entity_id`                            | text FK      | who posted it                                   |
| `region`                                     | text         | `fhnw` \| `aargau` \| `ch` — weight, not filter |
| `location` / `location_lat` / `location_lng` | text/double  | for the map                                     |
| `honorar_mode`                               | text         | `chf` \| `tausch` \| `unbezahlt`                |
| `honorar_text`                               | text         | "180/h", "Embedded-C-Coaching", "Pro Bono"      |
| `valid_until`                                | date         | past this → auto-deactivate                     |
| `status`                                     | text         | `active` \| `expired` \| `closed`               |
| `embedding`                                  | vector(1024) | so inserate are matchable in discovery          |
| `tsv`                                        | tsvector     | `GENERATED`                                     |
| `created_at`                                 | timestamptz  |                                                 |

### Supporting tables

- **`tags`** (`slug` PK, `label`) — subscribable tags.
- **`inserat_tags`** (`inserat_id`, `tag_slug`) — composite PK; the `#Security #OAuth` chips.
- **`entity_tags`** (`entity_id`, `tag_slug`) — composite PK; the skill chips on profile cards.
- **`tag_subscriptions`** (`user_id`, `tag_slug`) — composite PK; powers "subscribers get notified."
- **`users`** (`id` PK, `email` unique, `display_name`, `entity_id` FK) — account that claims one entity.
- **`source_connections`** (`id` PK, `entity_id` FK, `source_type`, `source_ref`, `sync_status`, `last_synced_at`) — the onboarding "3 von 5 Quellen verbunden" rows, each tracked separately from the documents they produce.

### Two modeling decisions worth remembering

- **The harvest-then-claim seam.** An entity is created by the harvester _first_ (no owner). A user later claims it during onboarding. Both `entities.owner_user_id` and `users.entity_id` exist; treat **`users.entity_id` as authoritative** and the other as a convenience, or the two will drift. When a user connects a source, match to the existing entity rather than inserting a duplicate.
- **Two search spaces.** `documents` and `inserate` each carry their own `embedding` + `tsv`. At query time you search both and merge with RRF. This is the one real coupling point; the escape hatch (if the merge ever gets gnarly) is a denormalized search-index table — not restructuring the source-of-truth tables.

---

## 4. The retrieval pipeline

A textbook **two-stage retrieve-and-rerank** pipeline, with an explainability stage bolted on.

### Stage 1 — Retrieval (high recall, runs instantly)

Cast a wide net using two methods in parallel, then fuse by rank.

**Dense search (semantic).** The query is embedded into a vector; pgvector's `<=>` cosine-distance operator finds nearest neighbors across `documents` and `inserate`. This is what makes the search cross-lingual — "Python Entwickler" and "Python Developer" land near each other in vector space.

**Lexical search (keyword).** Postgres full-text search (`tsv @@ plainto_tsquery('simple', $1)`, ranked by `ts_rank_cd`) catches exact terms — specific tech names, company names — that semantic search can blur. Note: lexical search is literal and single-language; the dense pass is the safety net for cross-language matches.

**Reciprocal Rank Fusion (RRF, k=60).** Rather than trying to normalize incompatible vector distances against lexical scores, RRF uses only each document's _rank position_. Each list contributes `1 / (60 + rank)`; documents appearing in both lists sum their contributions. The top document scores `1/61`, the second `1/62`, and so on. Simple, robust, no score calibration needed.

### Stage 2 — Re-rank (precision)

The fused candidate pool is re-ordered by business logic, not a second ML model.

**Entity rollup.** Group document hits by `entity_id` and sum their RRF scores into an aggregate entity score. Keep the top-3 documents per entity as evidence candidates.

**Scope weighting.** A multiplicative boost by network proximity — `fhnw: 1.3`, `aargau: 1.15`, `ch: 1.0`. An FHNW entity gets a 30% boost over a generic Swiss one. Scope is a _weight, never a hard filter_ — out-of-scope results still appear, just lower. Sort by the final weighted score; slice to the top results.

**Inserat filtering.** Inserat hits are filtered to `status = 'active' AND (valid_until IS NULL OR valid_until >= CURRENT_DATE)` so expired posts never surface.

### Stage 3 — Rationale (explainability, lazy)

For a chosen result, assemble its top-3 documents (and, if relevant, an owned inserat) into a context block with stable `[doc_id]` labels, then ask Gemini Flash for a German rationale plus a cited evidence list. The prompt forbids inventing IDs; every returned `ref_id` is validated against the set actually passed in.

---

## 5. What changed in v10

v10 is defined by four upgrades that turn the earlier proof-of-concept into something fast and demo-ready.

### 5.1 Semantic chunking (replaces character-count splitting)

Earlier versions split text by raw character count (`chunkSize = 1000`), which sliced sentences — even words — in half and destroyed the meaning the embedding model needs. v10 uses a recursive splitter (`@langchain/textsplitters` `RecursiveCharacterTextSplitter`) that tries paragraph breaks (`\n\n`) first, then lines (`\n`), then spaces, then characters as a last resort. Target ~800 chars with ~100 overlap. This keeps complete thoughts together and is the single highest-impact, lowest-effort fix.

### 5.2 Gemini Flash for rationale (replaces local Ollama generation)

Rationale generation moves from a local Ollama model to the Gemini Flash API. Two reasons:

- **Speed.** Local models generate sequentially; five rationales meant the user staring at a blank terminal for ~15 seconds. Flash returns in well under a second.
- **JSON reliability.** Flash supports `responseMimeType: "application/json"` with a response schema, which guarantees parseable output and eliminates the `JSON.parse` crashes that small local models cause when they drop a comma or hallucinate a key.

**Embeddings stay local** on Ollama (`multilingual-e5-large`) — only the _rationale_ moves to the cloud. A `try/catch` fallback still shows the result with a plain "no rationale available" note rather than dropping the entity.

> **Model-version note:** use whatever current Flash model the API exposes at build time rather than hard-coding a version string — earlier sessions confused "3.1 Flash" with "1.5 Flash"; neither matters, but a stale hard-coded name can 404.

### 5.3 Decoupled lazy loading (search separated from reasoning)

Earlier code bound retrieval and rationale together in one loop, capping results at 5 and blocking on the LLM. v10 splits them:

- **Stage 1 retrieves a larger pool** (TOP_K = 20) across documents and inserate — just Postgres vector math and FTS, so it returns almost instantly.
- **The ranked list renders immediately** with pre-computed data only (rank, name/title, type, location, scope, score) — **no LLM call**.
- **The readline loop accepts commands:** a number `N` generates and shows the Gemini rationale for result N on demand; `more` pages to the next 10; `new` starts a fresh query; `q` quits.

This is the core v10 idea: the list is instant and can show more than 5 results, while the expensive cited rationale is computed only for the one result the user actually drills into — keeping both latency and Gemini cost low.

### 5.4 Inserate in the search space

Inserate now participate in retrieval alongside documents (filtered to active + non-expired). Inserat results render directly; person/company results cite their top-3 documents, and if an entity owns a matching inserat, that inserat appears as evidence too. This is what lets the primary scenario work — Lisa's query surfaces both the people _and_ the relevant board posts in one ranked list.

---

## 6. The rationale contract

The Gemini call must return exactly this JSON shape, enforced by schema:

```json
{
  "warum": "string — German, 2-3 sentences explaining the match",
  "evidence": [
    { "ref_id": "string", "label": "string", "url": "string | null" }
  ]
}
```

Rules baked into the prompt (in German): _"erfinde KEINE IDs; nur die aufgeführten sind erlaubt"_ — invent no IDs, only the listed ones are allowed. After the call, every `ref_id` is validated against the set of document/inserat IDs passed in context; any that don't match are dropped. This is what makes the "evidence-forward, cited sources, no percentage score" UX honest rather than decorative — every footnote maps to a real row.

**One caveat:** not every footnote in the eventual UI is a `documents` or `inserate` row. Some (e.g. "Honorar-Deklaration · Modus chf", "Marktdaten · ICTswitzerland Honorar-Index") are _synthetic_ evidence derived from structured fields or external references. For the prototype, let the rationale layer emit evidence as a small JSON array of `{label, url, ref_id, kind}` rather than forcing every footnote to be a foreign key. Don't over-model this yet.

---

## 7. Data sources

| Source                 | Used for                                  | Access method                                   |
| ---------------------- | ----------------------------------------- | ----------------------------------------------- |
| GitHub                 | developer signal (repos, READMEs, bio)    | public API / unauthenticated                    |
| CV PDF upload          | the LinkedIn substitute (`linkedin_cv`)   | user upload — **there is no LinkedIn API path** |
| Personal website       | bio, blog posts, project descriptions     | fetch + clean                                   |
| ORCID / OpenAlex / ROR | researchers (scenario 2)                  | public APIs                                     |
| LINDAS SPARQL          | Swiss companies (Handelsregister / Zefix) | SPARQL endpoint                                 |
| IRF FHNW (OAI-PMH)     | FHNW research outputs                     | OAI-PMH harvest                                 |

A note from the design: the onboarding offers CV-PDF upload and ORCID/GitHub/Scholar/website links — deliberately **not** a live LinkedIn sync, because LinkedIn exposes no public API. Bake `linkedin_cv` (the uploaded PDF) into the source-type enum; do not build a `linkedin_api` path.

For the prototype, geocode `location` text into coordinates with a small hardcoded Aargau lookup table (Baden, Brugg, Wettingen, Aarau, Lenzburg) — the harvester yields town names as strings, not lat/lng, and the later map view needs coordinates.

---

## 8. File layout

```
prototyping/industryProfiles/
├── 10_schema.sql        # all tables, GENERATED tsv, HNSW indexes; re-runnable
├── 10_utils.js          # embedding (Ollama), Gemini rationale, semantic chunker,
│                        #   geocode lookup, db pool, shared config
├── 10_ingest.js         # sources → chunk → embed → upsert documents/inserate
└── 10_search_cli.js     # the decoupled 3-stage flow (Section 4)
```

Shared config at the top of `10_utils.js`: `TOP_K = 20`, `RRF_K = 60`, `SCOPE_WEIGHTS = { fhnw: 1.3, aargau: 1.15, ch: 1.0 }`, and the model names.

---

## 9. Definition of Done — when the CLI is ready

The CLI is **ready to demo** when all of the following hold. This is the checklist to run before declaring v10 complete and before touching any Spring Boot or React code.

### Functional criteria

1. **Schema applies cleanly and is re-runnable.** `10_schema.sql` creates all nine tables with `GENERATED` tsv columns and HNSW vector indexes, using `DROP ... IF EXISTS` guards so it can be run repeatedly without error.

2. **Ingestion populates the store with embeddings.** `10_ingest.js` fills `entities`, `documents`, `inserate`, and `tags`; every document and inserat has a non-null `embedding` and a populated `tsv`. Re-running it does not duplicate rows (upsert on stable IDs) and does not re-embed unchanged text (the `text_hash` check works).

3. **Search renders a list of more than 5 results instantly.** Running `10_search_cli.js` with a query returns the ranked list with **no LLM in the hot path** — the list appears in well under a second, showing rank, name/title, type, location, scope, and score.

4. **Rationale is lazy, cited, and crash-proof.** Typing a result number produces a German rationale whose every cited `ref_id` is a real, passed-in document/inserat ID (hallucinated IDs are dropped). Malformed Gemini output never kills the loop — it falls back gracefully. `more` pages correctly; expired inserate never appear anywhere.

5. **The primary scenario produces a believable result.** The query **"embedded firmware security review zwei wochen"** returns a sensible mix — at least one person, one company, and one or more inserate — each with cited evidence that a coach would find plausible. This is the single demo that proves the concept beats Vitamin B.

### Quality bar (what "good results" means)

The pipeline is **structurally** production-grade already; the risk to _good results_ is data quality, not architecture. Before the demo, verify:

- **Chunks hold complete thoughts.** Spot-check a few `documents.text` chunks — no sentences cut mid-word. (This is the semantic-chunking fix; it is the most common cause of a search "looking broken.")
- **Cross-language retrieval works.** A German query finds an English profile and vice versa (the dense pass should carry this even though lexical search is single-language).
- **Scope weighting is visible but not dominant.** An FHNW result ranks above an equally-relevant CH result, but a much stronger CH result still beats a weak FHNW one — scope is a nudge, not a gate.
- **Every rationale is grounded.** No rationale references a source that isn't in that entity's evidence list.

### Explicit stop point

When criteria 1–5 pass and the quality bar is met, **stop.** Do not begin Spring Boot controllers or React components. The purpose of v10 is to de-risk the ingestion-to-evidence pipeline; once it produces believable, cited results from real sources, the remaining web and UI work is well-understood plumbing that builds on a proven core.

---

## 10. Why this sequence is correct

Building the CLI before the Spring Boot/React stack is deliberate. The hard, unproven part of BadenConnect is not the REST layer or the UI — it is the **ingestion-to-evidence pipeline**: can you reliably turn messy public sources into structured, embedded documents, retrieve them well across languages, and have an LLM explain matches with _honest_ citations? If you went straight to Spring Boot, you would spend weeks on JPA wiring and controllers only to discover late that, say, the rationale model hallucinates citations or that chunking quality wrecks retrieval. The CLI surfaces those risks in days, on a lean Postgres-only stack, where they are cheap to fix. Once v10 proves the pipeline, the modulith and React frontend become an exercise in exposing a known-good engine — exactly the position you want to be in.
