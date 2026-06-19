# Orbit — Discovery Engine: Data Model & Schema Plan

This document explains *how* the Discovery Engine is structured at the data layer and *why*. It pairs the entity-relationship diagram with the SQL, grouped so each part of the schema is explained next to the reasoning behind it. It's a starting point to adapt inside the Spring Modulith, not a final migration.

---

## 1. Design at a glance

The schema is organized into **three zones**, and the central one is a **three-tier hierarchy**:

- **Core tiers (indigo): `entity → source → chunk`.** The retrieval target is a *person or organization*, not a document. Each entity has many sources (a GitHub repo, an ORCID work, a website), and each source is split into chunks — the unit hybrid search actually runs over. Scores roll up two levels (chunk → source → entity) so we rank entities, not fragments.
- **Scope & identity (teal).** `hubs` + `hub_memberships` let the user restrict results to a hub (FHNW, an accelerator). `entity_identifiers` is the backbone of entity resolution — it's how we recognize that a GitHub account and an ORCID profile belong to the same person.
- **Search lifecycle (amber): `anfragen → matches → match_citations`.** The request side. An Anfrage is created, matched against entities, and each match carries a grounded rationale plus the citations that justify it.

---

## 2. Core tiers — what we search and rank

The unit of search is the **chunk**, because `multilingual-e5-large` only embeds ~512 tokens at a time — a whole paper or website would be silently truncated. So sources are split into overlapping chunks, each embedded into a 1024-dim vector and given a `tsvector` for keyword search.

```sql
create extension if not exists vector;

create table entities (
  id              bigint primary key generated always as identity,
  entity_type     text not null,            -- 'person' | 'organization'
  display_name    text not null,
  headline        text,                     -- "CTO & Mitgründer"
  org_name        text,
  latitude        double precision,         -- for the "X Min. von FHNW" chip
  longitude       double precision,
  available_from  date,                     -- "Verfügbar ab 1. Juli"
  status          text not null default 'active',
  metadata        jsonb default '{}',
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now()
);

create table sources (
  id            bigint primary key generated always as identity,
  entity_id     bigint not null references entities(id) on delete cascade,
  source_type   text not null,    -- 'github' | 'linkedin_pdf' | 'zefix'
                                  -- | 'orcid_pub' | 'personal_site' | 'company_site'
  source_url    text,             -- citation chip target
  label         text,             -- chip text, e.g. «HIL-Testing at Scale» (2025)
  provenance    text not null default 'harvested',  -- 'user_uploaded' | 'harvested'
  consent_basis text,             -- e.g. 'user_consent' for uploaded LinkedIn PDFs
  raw_content   text,             -- keep raw so re-chunking needs no re-fetch
  content_hash  text,             -- skip re-embedding if unchanged
  fetch_status  text default 'ok',
  fetched_at    timestamptz,
  created_at    timestamptz not null default now()
);
create index on sources (entity_id);

create table chunks (
  id              bigint primary key generated always as identity,
  source_id       bigint not null references sources(id) on delete cascade,
  entity_id       bigint not null references entities(id),  -- denormalized for fast rollup
  chunk_index     int not null,
  content         text not null,            -- stored WITHOUT the 'passage:' prefix
  section         text,                     -- 'abstract' | 'methods' | page slug
  token_count     int,                      -- verify it stays under e5's 512 limit
  fts             tsvector generated always as (to_tsvector('german', content)) stored,
  embedding       vector(1024),             -- multilingual-e5-large
  embedding_model text default 'multilingual-e5-large',  -- so we can re-embed safely
  embedded_at     timestamptz,
  created_at      timestamptz not null default now()
);
create index on chunks using gin  (fts);
create index on chunks using hnsw (embedding vector_ip_ops);
create index on chunks (entity_id);
create index on chunks (source_id);
```

Two deliberate choices here. `sources` doubles as the **citation layer** — `source_url` + `label` are literally what render as the chips on a result card. And `chunks.embedding_model` exists because we experiment with different embedding models; versioning which model produced a vector means a model swap is a tracked re-embed, not a guessing game.

---

## 3. Identity — entity resolution

The hardest sub-problem: a single founder appears across GitHub, ORCID, LinkedIn, and a personal site. If those land as four unlinked entities, search returns four fragments of one person. `entity_identifiers` solves this — before creating a new entity during ingestion, we look it up by a stable external id.

```sql
create table entity_identifiers (
  id          bigint primary key generated always as identity,
  entity_id   bigint not null references entities(id) on delete cascade,
  id_type     text not null,    -- 'orcid' | 'github_login' | 'zefix_uid' | 'linkedin_url' | 'domain'
  id_value    text not null,
  unique (id_type, id_value)    -- one external id maps to exactly one entity
);
```

The `unique (id_type, id_value)` constraint is what enforces "one ORCID = one entity." This is a recognized record-linkage problem and a legitimate, citable part of the thesis — built *after* one ingestion path works end to end.

---

## 4. Scope — hubs

A hub is a namespace; an entity can belong to several at once (a startup can be both accelerator- and FHNW-affiliated), so membership is a **many-to-many join table**, not a column on `entities`.

```sql
create table hubs (
  id         bigint primary key generated always as identity,
  slug       text not null unique,          -- 'fhnw', 'startup-accelerator-aargau'
  name       text not null,
  hub_type   text,                          -- 'university' | 'accelerator' | 'region'
  latitude   double precision,              -- anchor for distance chips
  longitude  double precision,
  created_at timestamptz not null default now()
);

create table hub_memberships (
  entity_id   bigint not null references entities(id) on delete cascade,
  hub_id      bigint not null references hubs(id)     on delete cascade,
  affiliation text,                          -- 'student' | 'alumni' | 'portfolio' | 'partner'
  joined_at   timestamptz not null default now(),
  primary key (entity_id, hub_id)
);
create index on hub_memberships (hub_id);    -- scope lookups go hub -> entities
```

When the user picks a hub, the scope is a **hard filter** applied *inside both CTEs* of the hybrid-search function (joining to `hub_memberships` before the per-leg limit), combined with pgvector's iterative scan so a small hub like FHNW isn't starved. The same table also supports a future *soft-boost* mode (bonus to RRF score instead of a filter) if we want it.

---

## 5. Search lifecycle — and why parts of it are persisted

```sql
create table anfragen (
  id              bigint primary key generated always as identity,
  created_by      bigint,                   -- FK to users
  query_text      text not null,
  query_embedding vector(1024),             -- embed once, reuse
  scope_hub_ids   bigint[],                 -- chosen hubs (empty = all)
  status          text not null default 'open',  -- 'open' | 'matched' | 'closed'
  created_at      timestamptz not null default now()
);

create table matches (
  id           bigint primary key generated always as identity,
  anfrage_id   bigint not null references anfragen(id) on delete cascade,
  entity_id    bigint not null references entities(id) on delete cascade,
  score        double precision,            -- entity-level RRF score after rollup
  rationale    text,                         -- the German explanation on the card
  claims       jsonb,                        -- [{text, source_ids:[...]}] from the generator
  created_at   timestamptz not null default now(),
  unique (anfrage_id, entity_id)
);

create table match_citations (
  match_id   bigint not null references matches(id)  on delete cascade,
  source_id  bigint not null references sources(id)  on delete cascade,
  primary key (match_id, source_id)
);
```

**`anfragen` is unconditionally first-class** — it *is* the Anfrage-Board, Orbit's second surface. A request a user creates, that stays open and that others can respond to, has to be persisted regardless of search.

**`matches` + `match_citations` are a deliberate trade-off, not a default.** Generating a rationale costs a Gemini Flash call per shown entity and is non-deterministic. We persist them because:
- A match gets **pinned to an Anfrage on the board** and seen by multiple people over time — a rationale that silently changes between views would look broken.
- It avoids paying the generation cost again on every view.
- It gives the thesis an auditable dataset to compute **citation precision** on (you can't audit grounding you discarded).

The cost is **staleness**: if an entity updates a source after the match was generated, the stored rationale can drift. We mitigate with a freshness rule — regenerate when a source's `content_hash` changed after `matches.created_at`. *If matching were a purely transient live search never pinned to the board, `matches`/`match_citations` could be dropped and generated on the fly — the rest of the schema is unaffected.*

`match_citations` is the loop closing: the validated citations are exactly the chips on the card, pointing back to the source that grounds each claim — so a citation can never reference evidence that wasn't retrieved.

---

## 6. Cross-cutting decisions worth flagging

- **Denormalized `chunks.entity_id`.** The real path is `chunk → source → entity`, but carrying `entity_id` directly on the chunk makes the entity rollup a single `GROUP BY` instead of a join. Shown dashed in the diagram to mark it as a denormalization, not a second truth.
- **Provenance & consent on `sources`.** User-uploaded LinkedIn PDFs and personal data fall under revFADP / GDPR; `provenance` + `consent_basis` record the lawful basis. (User upload is a defensible basis; scraping LinkedIn would not be.)
- **Soft-delete via `status`.** `entities.status` supports hiding without deleting; decide a hard-vs-soft policy before launch.
- **`claims` as `jsonb`.** Maps cleanly to the generator's output; normalize into a `match_claims` table later only if you need to query individual claims.

---

## 7. Open decisions (not yet in the schema)

- A **users/accounts** table — the `created_by` FKs point at it.
- Whether `claims` stays `jsonb` or gets normalized.
- **Spring Modulith boundaries.** These tables likely split across modules — e.g. *discovery* (`entities`, `sources`, `chunks`, `hubs`) and *requests* (`anfragen`, `matches`). FKs that cross a module boundary deserve a deliberate decision: Modulith generally prefers referencing by ID across modules over hard cross-module FK relationships, so some of the amber→indigo links may become application-level references rather than database constraints.
