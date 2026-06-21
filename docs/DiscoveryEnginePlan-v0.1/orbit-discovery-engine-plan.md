# Orbit — Discovery Engine: Grounded Matching with Cited Reasoning

**Feature goal.** Given an Anfrage (a structured request, e.g. *"Suche Embedded-Team mit Zephyr + CI für Hardware-Tests"*) — optionally **scoped to a hub** (FHNW, a startup accelerator, a region) — return a ranked list of **entities** (people / organizations) where each result card shows a German rationale that explains *why* this entity matches, and every claim in that rationale is backed by a citation chip pointing to the actual source that supports it (paper, GitHub repo, website).

This document is the high-level plan. It covers the data model, ingestion, chunking, embedding, scope filtering, retrieval, ranking, and the grounded-reasoning step — and how they connect so a citation can never reference evidence that wasn't retrieved.

---

## 1. Core principle: match entities, not documents

The retrieval target is a **person or organization**, not a PDF or a webpage. A single founder may appear across LinkedIn, GitHub, ORCID, a personal site, and Zefix. If those land as five unlinked documents, the engine returns five fragments of one person instead of one strong match.

Everything below follows from a **three-tier hierarchy**:

```
entity  (person / org)   ← the thing we rank and show on a card
  └─ source  (one GitHub repo, one ORCID work, one website, one PDF…)
       └─ chunk  (an embeddable unit ≤ ~400 tokens, what we actually search over)
```

Hybrid search runs over **chunks**. Scores roll up **two levels**: chunk → source → entity. The reasoning step then explains the entity using only the chunks that won.

---

## 2. Data model

```sql
-- The thing we rank and display
create table entities (
  id           bigint primary key generated always as identity,
  entity_type  text,          -- 'person' | 'organization'
  display_name text,
  headline     text,          -- "CTO & Mitgründer", "Embedded-Firmware-Entwicklerin"
  org_name     text,
  location     text,          -- for the "X Min. von FHNW Brugg" chip
  metadata     jsonb          -- ORCID iD, Zefix UID, availability date, etc.
);

-- One source feed attached to an entity
create table sources (
  id          bigint primary key generated always as identity,
  entity_id   bigint references entities(id) on delete cascade,
  source_type text,           -- 'github' | 'linkedin_pdf' | 'zefix' |
                              -- 'orcid_pub' | 'personal_site' | 'company_site'
  source_url  text,           -- becomes the citation chip target
  label       text,           -- chip text, e.g. «HIL-Testing at Scale» (Embedded World 2025)
  fetched_at  timestamptz
);

-- The searchable unit
create table chunks (
  id          bigint primary key generated always as identity,
  source_id   bigint references sources(id) on delete cascade,
  entity_id   bigint references entities(id),   -- denormalized for fast rollup
  chunk_index int,
  content     text,
  section     text,
  fts         tsvector generated always as (to_tsvector('german', content)) stored,
  embedding   extensions.vector(1024)            -- multilingual-e5-large = 1024 dims
);

create index on chunks using gin(fts);
create index on chunks using hnsw (embedding vector_ip_ops);

-- Hubs: scope namespaces (FHNW, an accelerator, a region)
create table hubs (
  id   bigint primary key generated always as identity,
  slug text,                  -- 'fhnw', 'startup-accelerator-aargau'
  name text
);

-- Many-to-many: an entity can belong to several hubs at once
create table hub_memberships (
  entity_id bigint references entities(id) on delete cascade,
  hub_id    bigint references hubs(id)     on delete cascade,
  primary key (entity_id, hub_id)
);
```

`sources` is the citation layer: `source_url` + `label` are exactly what render as the chips under the rationale.

`hubs` + `hub_memberships` are the scope layer. Membership is **many-to-many** (a startup can be both accelerator- and FHNW-affiliated), so it's a join table, not a column on `entities`. The frontend sends the selected `hub_id`(s) with the Anfrage.

---

## 3. Ingestion — what to extract per source

Embed the high-signal core, keep the rest as filterable metadata. Avoid embedding raw text uniformly.

| Source | Embed this | Keep as metadata |
|---|---|---|
| **GitHub repos** | README + description + topics (not code) | stars, primary language, last commit |
| **LinkedIn PDF (user-uploaded)** | headline, About, each role (split per section) | dates, company names |
| **Zefix company desc.** | the **Zweck** (registered purpose) | UID, legal form, status, address |
| **ORCID publications** | title + abstract (enrich via OpenAlex/Crossref) | year, DOI, co-authors |
| **Personal websites** | About / projects (strip nav/footer/cookie) | domain, last-fetched |
| **Company websites** | About / products / team | domain |

> **Note:** "Zeflix" = **Zefix** (zefix.admin.ch), the Swiss commercial register — confirm this is the intended source.

---

## 4. Chunking strategy

- **Default:** structure-aware recursive splitting — break on the largest natural boundary that fits (section → paragraph → sentence).
- **Target size:** ~250–400 tokens per chunk, **10–20% overlap** so context isn't cut mid-thought.
- **Why it's mandatory:** `multilingual-e5-large` has a **512-token** max sequence. Anything longer is silently truncated — embedding a whole paper means ~90% never reaches the vector.
- **Papers:** split along sections (abstract, intro, methods, results); store the heading in `section`.
- **Websites:** strip boilerplate first, then chunk by section.
- **Short sources** (Zefix Zweck, ORCID abstract): usually a single chunk.

Implementation (Java / LangChain4j):

```java
DocumentSplitter splitter = DocumentSplitters.recursive(
    400,  // max tokens
    60,   // overlap
    new HuggingFaceTokenizer("intfloat/multilingual-e5-large")
);
```

Count tokens with the **actual e5 tokenizer**, not chars/4 — German compounds diverge a lot.

---

## 5. Embedding

- Model: `multilingual-e5-large`, **1024 dims**.
- **Prefixes are required:** store passages as `passage: <text>`, embed queries as `query: <text>`. Skipping this measurably hurts retrieval.
- FTS config: `german` (or `simple` if heavily mixing languages).

---

## 6. Retrieval + ranking

### 6a. Scope filter (hard) — applied *inside* retrieval, not after
When the user selects a hub, scope is a **hard filter on the candidate set**. The critical rule: the filter joins to `hub_memberships` **inside both CTEs of `hybrid_search`**, before the per-leg `limit`. Filtering after the search would let out-of-scope rows consume the limit and starve a small hub (FHNW might be 200 entities out of ~700k).

```sql
create or replace function hybrid_search(
  query_text       text,
  query_embedding  extensions.vector(1024),
  scope_hub_ids    bigint[],          -- the hard filter (one or several hubs)
  match_count      int,
  full_text_weight float = 1,
  semantic_weight  float = 1,
  rrf_k            int   = 50
)
returns setof entities                -- returns entities, not chunks
language sql
as $$
with full_text as (
  select c.entity_id,
         row_number() over (
           order by ts_rank_cd(c.fts, websearch_to_tsquery('german', query_text)) desc
         ) as rank_ix
  from chunks c
  join hub_memberships m
    on m.entity_id = c.entity_id and m.hub_id = any(scope_hub_ids)   -- filter
  where c.fts @@ websearch_to_tsquery('german', query_text)
  limit least(match_count, 30) * 4
),
semantic as (
  select c.entity_id,
         row_number() over (order by c.embedding <#> query_embedding) as rank_ix
  from chunks c
  join hub_memberships m
    on m.entity_id = c.entity_id and m.hub_id = any(scope_hub_ids)   -- filter
  limit least(match_count, 30) * 4
),
fused as (
  select coalesce(f.entity_id, s.entity_id) as entity_id,
         coalesce(1.0 / (rrf_k + f.rank_ix), 0.0) * full_text_weight +
         coalesce(1.0 / (rrf_k + s.rank_ix), 0.0) * semantic_weight as score
  from full_text f
  full outer join semantic s on f.entity_id = s.entity_id
)
select e.* from fused
join entities e on e.id = fused.entity_id
order by fused.score desc
limit least(match_count, 30);
$$;
```

**pgvector gotcha — small scopes break naive HNSW filtering.** HNSW is approximate over the *whole* graph; with a `WHERE`/join it can return its global top-k and have almost none survive the filter. Fix with **iterative scan** (pgvector ≥ 0.8), set per transaction before calling:
```sql
SET LOCAL hnsw.iterative_scan = 'strict_order';  -- keeps scanning until enough in-scope rows
```
From Spring, set the GUC and RPC in the same transaction (`SET LOCAL` so it doesn't leak onto pooled connections):
```java
jdbc.execute("SET LOCAL hnsw.iterative_scan = 'strict_order'");
var matches = jdbc.query(
    "select * from hybrid_search(?, ?::vector, ?, ?)",
    rowMapper, queryText, embeddingLiteral, hubIds, matchCount);
```
For larger fixed hubs, partial indexes (`... where hub_id = …`) or partitioning `chunks` by hub are later scaling options. Start with iterative scan — one line, covers realistic hub sizes.

> **Scope is a hard boundary here** (filter), but the same `hub_memberships` table also supports a future **soft-boost** mode (add a bonus to the RRF score for in-hub entities instead of a `WHERE`). That's a deliberate product decision worth a sentence in the thesis. A hard filter also needs an **empty-result fallback** — the prototype's *Anfrage erstellen* path is the natural one.

### 6b. Hybrid search over chunks (RRF)
Full-text (`tsvector` + GIN) and semantic (`pgvector` + HNSW) run separately, then fuse with **Reciprocal Rank Fusion**: `score = Σ 1/(k + rank_in_list)`, default `k = 50`, with tunable `full_text_weight` / `semantic_weight`. (Base function = the Supabase `hybrid_search` pattern, pointed at `chunks`.)

### 6c. Two-level rollup to entity
```sql
-- chunk scores → entity scores
select entity_id, max(rrf_score) as score
from scored_chunks
group by entity_id
order by score desc
limit candidate_count;
```
`max` per entity = "most relevant entity" (one strong chunk shouldn't lose to many mediocre ones). `sum` rewards matching on multiple fronts. **A/B these against real Anfragen** — it's a thesis-worthy comparison.

**Carry the top 3–5 winning chunks + their `source_id` per entity forward** — this is the evidence the reasoning step will explain.

### 6d. (Optional) cross-encoder rerank
Between rollup and generation, a multilingual reranker (e.g. `bge-reranker-v2-m3`) sharpens which entities reach generation. Optional; add if latency budget allows.

---

## 7. Grounded reasoning with validated citations

**The decision that ties ranking to explanation:** generate the rationale from *exactly the winning chunks*, not the full entity profile. The model explains the same evidence that drove the score, and can only cite what was retrieved. Generate only for the ~10 entities actually shown (keeps Gemini Flash cheap).

### Input to the generator
```json
{
  "anfrage": "Suche Embedded-Team mit Zephyr + CI für Hardware-Tests …",
  "entity": { "name": "David Aregger", "role": "CTO & Mitgründer",
              "org": "Sentio Robotics GmbH, Baden" },
  "evidence": [
    { "id": "S1", "type": "publication",
      "label": "«HIL-Testing at Scale» (Embedded World 2025)", "text": "…" },
    { "id": "S2", "type": "github",
      "label": "github.com/sentio-robotics/zephyr-ci", "text": "<readme chunk>" },
    { "id": "S3", "type": "website",
      "label": "sentio-robotics.ch", "text": "<about chunk>" }
  ]
}
```

### Required output (JSON only, German, low temperature)
```json
{
  "rationale": "Davids Startup baut Inspektionsroboter …; sein Team hat Erfahrung mit Zephyr und CI für Hardware-Tests, dokumentiert in zwei öffentlichen Repos und einem Konferenzbeitrag.",
  "claims": [
    { "text": "Erfahrung mit Zephyr und Hardware-Test-CI", "source_ids": ["S1","S2"] }
  ],
  "cited_source_ids": ["S1","S2"]
}
```

**System-prompt rules:** use only provided evidence; make no claim that isn't supported; cite source id(s) for every claim; respond in German; output JSON only. Constrain with `responseSchema` where supported.

### The non-negotiable validation step (Java)
```java
Set<String> allowed = evidence.stream().map(Evidence::id).collect(toSet());
result.claims().removeIf(c -> !allowed.containsAll(c.sourceIds()));
List<Evidence> chips = evidence.stream()
    .filter(e -> result.citedSourceIds().contains(e.id()))
    .toList();   // ← these become the citation chips on the card
```
A citation referencing an ID we didn't pass in is dropped — so a hallucinated citation can never reach the UI. Surviving `cited_source_ids` map straight back to chip rows.

### The closed loop
```
Anfrage ─▶ hybrid_search(chunks) ─▶ RRF ─▶ rollup to entity (+winning chunks)
        ─▶ [rerank] ─▶ generate rationale FROM winning chunks
        ─▶ validate citations ─▶ render card: rationale + chips
```
Same objects flow end to end: the chunks that ranked the entity are the evidence that grounds the rationale and the chips that prove it.

---

## 8. Privacy / legal (for the concept doc)

LinkedIn PDFs and personal-website data on identifiable people are personal data under **revFADP / GDPR**. User-uploading-their-own-profile is a defensible consent basis; note lawful basis, retention, and that LinkedIn ToS forbids scraping (user upload sidesteps this, scraping would not). Supervisors / PowerPlant will likely ask.

---

## 9. Evaluation (thesis-worthy, measurable)

- **Retrieval quality:** precision@k / recall@k of returned entities vs. a labeled set of Anfragen.
- **Ranking ablation:** RRF weights, `max` vs `sum` rollup, with/without reranker.
- **Citation precision:** of cited sources, how many actually support the claim — directly measures grounding, the property users trust the platform on.
- **Chunking hyperparameters:** size / overlap / aggregation reported as evaluated choices, not assertions.

---

## 10. Suggested build order

1. **Schema** — entities / sources / chunks + indexes, plus `hubs` / `hub_memberships`.
2. **One ingestion path end-to-end** (GitHub README is simplest) → chunk → embed → store; assign entities to at least one hub.
3. **Hybrid search + RRF over chunks** (port Supabase function).
4. **Scope filter** — add the `scope_hub_ids` join inside both CTEs + iterative scan.
5. **Entity rollup** query + carry winning chunks.
6. **Grounded generation + citation validation** (Gemini Flash, JSON schema).
7. **Card rendering** (rationale + chips, hub selector in UI) — matches the prototype.
8. Add remaining sources; add entity resolution; add reranker; run evaluation.

> **Entity resolution** (linking the 5+ sources of one person) is the genuinely hard subproblem — a citable record-linkage task in its own right. Build it after one path works end to end.

---

*Aside, not part of this feature: both prototype screenshots show a pinned `[bundle] Script error` overlay — a build/runtime JS error in the prototype itself. Worth clearing before the supervisor demo.*
