# Orbit — Discovery Engine: Unified Technical Reference (v2)

The single source of truth for the Discovery Engine. Stack: **Java Spring Boot monolith, JPA over PostgreSQL + pgvector**, `multilingual-e5-large` embeddings (1024 dims), Gemini Flash for grounded reasoning.

**What changed in v2.** This revision tracks **Orbit Spec v2** (Norbert feedback, 22.06.2026) and the HighFi flows (Demand / Supply / Onboarding). The retrieval core is unchanged on purpose — three-tier `entity → source → chunk`, hybrid BM25 + pgvector, RRF, `max` rollup, persisted grounded matches with validated citations all stay exactly as they were. What v2 *adds*:

- **Scope is now Hub → Region → CH**, not a flat list of hubs and not a radius. Hubs roll into regions, regions into CH; the hard filter widens up that tree (§5).
- **Geography, affiliation, and presence are three separate axes** (§5.2, §7). Distance is a re-rank signal, not a hub.
- **Structured availability** (date range + Pensum % + preferred collaboration) lives on the entity and drives re-ranking (§6.3).
- **A real re-ranking stage** after retrieval: availability overlap, presence alignment, proximity, hub overlap (§6).
- **Pull vs push are different mechanisms.** Live *search* is semantic (the embedding pipeline). *Distribution* of an Anfrage to standing profiles is **rule-based on tags + scope** so it stays explainable ("verteilt, weil dein Profil die Tags … trägt") (§8).
- **Anfrage lifecycle** Offen → Verteilt → Kontaktiert → Geschlossen, with urgency-driven widening (§8).
- **Tags / skills / subscriptions**, **accounts + verified email**, **Person↔Org links**, **HR-verified**, and the **first-contact handoff with sender context** (§9–§11).

**How this doc is meant to be used:** the schema is introduced **incrementally** — each implementation stage (§14) ships its own Flyway migration that creates *only* the tables that stage needs. The codebase never carries entities for features that aren't built yet. §3 is the map of which table arrives in which stage; the actual `CREATE TABLE` lives inside each stage. The full grounded vertical (scoped search → grounded card) still works end-to-end by **Stage 4**; everything after that layers richness onto a working system.

## Contents
1. What we're building
2. Architecture at a glance
3. Table catalog (what arrives when)
4. Ingestion pipeline — detailed
5. Scope model — Hub → Region → CH
6. Discovery & match pipeline — retrieve → re-rank → ground
7. Hard vs soft filters (the filter reference)
8. Anfrage lifecycle & distribution
9. Tags, skills & subscriptions
10. Identity, accounts & Person↔Org links
11. Contact handoff (sender context, gating)
12. End-to-end trace
13. JPA / monolith implementation notes
14. Schema
15. Open decisions

---

## 1. What we're building

Orbit is **asymmetric**: *Nachfrage handelt, Angebot wartet.* Demand is an active, lifecycle-bearing **Anfrage**; supply is a standing **profile** that is found, never a post. Both sides write into one data model — there is no "Angebot" object.

The engine serves two distinct jobs:

- **Pull (search).** Given an **Anfrage / query** (e.g. *"Suche Embedded-Team mit Zephyr + CI für Hardware-Tests"*), scoped to **Alle / a hub / a region**, return ranked **entities** (people / organizations). Each result card shows a German **rationale** with a **citation chip** per claim, a **distance** ("12 Min. von FHNW Brugg"), and a structured **availability pill** ("60 % · 1. Aug – 31. Okt"). This is the semantic pipeline (§6).
- **Push (distribution).** When a query isn't enough, the Anfrage becomes a persistent public object on the Anfrage-Board and is **distributed** to matching standing profiles and tag-subscribers. Distribution is **rule-based on tags + scope**, not semantic, so the "why you were notified" reason is exact and explainable (§8).

Defining property of the pull path (unchanged from v1): **a citation can never reference evidence that wasn't retrieved.** The chunks that rank an entity are the same chunks that ground its rationale and produce its chips.

---

## 2. Architecture at a glance

![Orbit Discovery Engine schema diagram](orbit-schema-diagram.svg)

> Diagram file: `orbit-schema-diagram.svg`, kept alongside this document — it renders as an image. If it doesn't resolve in your viewer, open the SVG directly.

Five zones. The centre is still the three-tier hierarchy `entity → source → chunk` — we search over chunks, roll scores up to entities, scope by the geography zone, re-rank by the soft signals, explain matches with cited reasoning, and (for push) distribute by tags.

- **Core tiers** (`entities`, `sources`, `chunks`) — searched & ranked. `entities` now also carries structured availability and affiliation.
- **Geo & scope** (`regions`, `hubs`, `hub_memberships`) — the Hub → Region → CH tree and the hard-filter join.
- **Identity & relationships** (`entity_identifiers`, `accounts`, `entity_links`) — resolution/dedup, login, Person↔Org.
- **Tags & distribution** (`tags`, `entity_tags`, `anfrage_tags`, `tag_subscriptions`, `anfrage_distributions`) — the push path.
- **Request lifecycle** (`anfragen`, `matches`, `match_citations`, `contact_requests`) — request → grounded result → first contact.

---

## 3. Table catalog (what arrives when)

The full schema, each table tagged with the stage that creates it. Nothing is created before the feature that uses it. Columns added to an existing table by a later stage are noted as **ALTER**.

| Table / change | Zone | Purpose | Created in |
|---|---|---|---|
| `entities` | core | the thing we rank & show on a card | **Stage 1** |
| `entity_identifiers` | identity | entity resolution / dedup (one ORCID = one entity) | **Stage 1** |
| `sources` | core | one evidence feed per entity; also the citation layer | **Stage 1** |
| `chunks` | core | the searchable unit (embedding + fts) | **Stage 1** |
| `regions` | geo/scope | region tree; CH is the root | **Stage 3** |
| `hubs` | geo/scope | affiliation namespace; belongs to a region | **Stage 3** |
| `hub_memberships` | geo/scope | many-to-many entity↔hub; the hard-filter join | **Stage 3** |
| `entities.home_region_id` (ALTER) | core | region anchor (derived from location) | **Stage 3** |
| `anfragen` | lifecycle | the request / Anfrage-Board object | **Stage 4** |
| `matches` | lifecycle | persisted grounded rationale per (anfrage, entity) | **Stage 4** |
| `match_citations` | lifecycle | validated citations = the chips | **Stage 4** |
| `entities` availability cols (ALTER) | core | `open_to_requests`, `available_from/to`, `pensum_pct`, `availability_note`, `preferred_collaboration[]`, `languages[]` | **Stage 5** |
| `tags` | tags | controlled tag vocabulary | **Stage 6** |
| `entity_tags` | tags | Kompetenzen (skills), derived + manual | **Stage 6** |
| `anfrage_tags` | tags | tags carried by an Anfrage | **Stage 6** |
| `tag_subscriptions` | tags | abonnierte Themen (entity↔tag) | **Stage 6** |
| `anfrage_distributions` | distribution | who was notified, why, at which widening level | **Stage 6** |
| `accounts` | identity | login + verified email; owns one entity | **Stage 7** |
| `entity_links` | identity | Person↔Organisation | **Stage 7** |
| `entities.affiliation_kind`, `.hr_verified` (ALTER) | core/identity | Firma / Selbständig / Privatperson; HR-verified badge | **Stage 7** |
| `contact_requests` | lifecycle | first-contact handoff with sender context | **Stage 8** |

Functions: `hybrid_search` (unscoped) arrives in **Stage 2**, is replaced by the scoped Hub→Region version in **Stage 3**. Re-ranking (§6) and distribution (§8) are **application-layer** (no DB function), so they add no migration of their own.

---

## 4. Ingestion pipeline — detailed

Ingestion runs **per source** and must be **idempotent** (safe to re-run) and **convergent** (multiple sources for the same person land on one entity). The ordering is load-bearing: **resolve identity before creating anything; dedup before embedding.** v2 adds three derivation outputs to ingestion — **tags** (Kompetenzen), **hub/region suggestions**, and **affiliation** — all *proposed* and then confirmed by the user in onboarding (spec §14.5 / §10).

### 4.1 Per-source extraction spec

| `source_type` | Fetch mechanism | Embed (high-signal core) | `label` (chip text) | External id(s) for resolution | Also derive |
|---|---|---|---|---|---|
| `github` | GitHub REST `/repos/{owner}/{repo}` + `/readme` | README (stripped of badges/HTML) + description + topics | `github.com/{owner}/{repo}` | `github_login` = `{owner}` | tags (topics, languages); stars, primary lang, pushed_at |
| `orcid_pub` | ORCID `/works` → DOIs → OpenAlex/Crossref for title+abstract | title + abstract (one chunk if ≤512 tok) | `«{title}» ({year})` | `orcid`; also `doi` | tags (concepts); year, venue; **ORCID affiliation → hub** |
| `zefix` | Zefix API or LINDAS SPARQL | the **Zweck** (registered purpose) | company legal name | `zefix_uid` = UID (CHE-…) | legal form, status, address → **home_region**; sets `hr_verified=true` |
| `personal_site` | crawl; strip nav/footer/cookie/scripts | About + projects | domain | `domain` (registrable) | tags; last-fetched, title |
| `company_site` | crawl | About / products / team | domain | `domain` | tags; team names → candidate `entity_links` |
| `linkedin_pdf` | user upload → PDF text extraction | headline + About + each role | "LinkedIn-Profil" | `linkedin_url` | tags; employer → **affiliation + hub candidate**; languages |
| `manual_text` | user-typed 2–3 sentences (Demand min profile) | the text itself (one chunk) | "Eigene Beschreibung" | none (attaches to the signed-in entity) | tags (light); **consent_basis = self_declared** |

Common rules: **strip boilerplate before embedding** (cookie banners, nav, badge images, license headers) — embedding noise degrades retrieval more than missing text does. **Languages** (DE/EN/FR from the onboarding chips or parsed from sources) are stored on `entities.languages[]`, not as a source.

### 4.2 The ingestion algorithm (precise)

```text
ingestSource(ref):     # ref = {url, source_type, provenance, consent_basis, hub_slug?, region_slug?}

  # 1. FETCH ---------------------------------------------------------------
  raw       = fetchRaw(ref)                       # API / SPARQL / crawl / PDF parse (see 4.1)
  coreText  = extractHighSignalCore(raw, ref.source_type)
  label     = buildLabel(raw, ref.source_type)
  hash      = sha256(normalize(coreText))         # normalize = trim + collapse ws + lowercase

  # 2. DEDUP ---------------------------------------------------------------
  existing = sources.findByUrl(ref.url)
  if existing != null and existing.content_hash == hash:
      return SKIPPED                              # unchanged → no re-embed, no re-derive

  # 3. RESOLVE IDENTITY (before creating an entity) ------------------------
  ids = extractExternalIds(raw, ref.source_type)
  entity = entityIdentifiers.findEntityByAnyId(ids)
  if entity == null:
      entity = entities.create(deriveProfile(raw, ref.source_type))   # name, headline, org, lat/lng
      entityIdentifiers.insertAll(entity.id, ids)
  else:
      entityIdentifiers.insertMissing(entity.id, ids)                 # converge ids onto one entity

  # 4. UPSERT SOURCE -------------------------------------------------------
  source = sources.upsertByUrl(entity.id, ref.source_type, ref.url, label,
                               ref.provenance, ref.consent_basis, raw.text, hash, 'ok', now())

  # 5. RE-CHUNK (delete old chunks for this source, then rebuild) ----------
  chunks.deleteBySource(source.id)
  segments = recursiveSplit(coreText, maxTokens=400, overlapTokens=60,
                            tokenizer=e5Tokenizer, boundaries=[SECTION,PARAGRAPH,SENTENCE])

  # 6. EMBED + PERSIST CHUNKS ---------------------------------------------
  for i, seg in enumerate(segments):
      vec = embed("passage: " + seg.text)         # e5 REQUIRES the 'passage: ' prefix
      chunks.insert(source.id, entity.id, i, seg.text, seg.section, seg.tokenCount,
                    vec, 'multilingual-e5-large', now())

  # 7. DERIVE (Stage 3/6/7) — proposed, not auto-applied -------------------
  proposeTags(entity.id, deriveTags(raw, ref.source_type))            # Stage 6: entity_tags(origin='derived')
  proposeHub(entity.id, deriveHub(raw, ref))                          # Stage 3: hub_memberships(source='derived')
  setRegionIfKnown(entity.id, deriveRegion(raw, ref))                 # Stage 3: entities.home_region_id
  setAffiliationIfKnown(entity.id, deriveAffiliation(raw))            # Stage 7: affiliation_kind + entity_links

  return INGESTED(entity.id, source.id, len(segments))
```

### 4.3 Detail that matters for a correct implementation

- **Idempotency key = `sources.url`.** Re-ingesting the same URL updates the existing row (`upsertByUrl`) and rebuilds its chunks; `content_hash` short-circuits before any embedding when nothing changed.
- **Entity resolution (`findEntityByAnyId`).** Query `entity_identifiers` for any `(id_type,id_value)` in `ids`; if ids resolve to *different* entities, that's an entity-merge case — Stage 1: log + attach to the first; proper merge is Stage 9.
- **Chunking.** `maxTokens=400`, `overlapTokens=60` (~15%). Split on the largest boundary that fits: section → paragraph → sentence. Keep `section` for filtering / optional prepend.
- **Token counting.** Use the real e5 tokenizer (HF `intfloat/multilingual-e5-large` via DJL) — German compounds make chars/4 badly wrong. Store `token_count`, assert `< 512`.
- **Embedding prefixes are not optional.** Stored passages `"passage: " + text`; query side `"query: " + text`. `content` is stored **without** the prefix.
- **Embedding source.** Local Ollama or LM Studio serving `multilingual-e5-large`; one vector per chunk; batch where supported.
- **Derivation is *proposed*, never silently authoritative.** Derived tags/hubs/affiliation are written with an origin flag (`origin='derived'`, `source='derived'`) and surfaced in onboarding for the user to **confirm or change** (spec §14.5). User edits set `origin='manual'` and are never overwritten by a later re-harvest.
- **Failure handling.** On fetch failure set `sources.fetch_status='error'`, skip chunk rebuild (keep last good chunks). Ingestion is a queued/scheduled job per source; a full re-harvest is a re-run with dedup absorbing the no-ops.

---

## 5. Scope model — Hub → Region → CH

v1 had a flat list of hubs and a `scope_hub_ids` array. v2 replaces that with the spec's **Hub → Region → CH** hierarchy, because the old radius model pretended a nesting that doesn't exist (FHNW-Brugg is in Aargau, FHNW-Olten is in Solothurn — "FHNW" is not a subset of "Aargau"). **Affiliation** (a hub) and **geographic proximity** are now separate concepts.

### 5.1 The three structures

- **`regions`** form a tree. `CH` is the root (`parent_region_id = null`). The UI's *Region Baden → Aargau → Schweiz* is just three nodes on that tree; the doc's canonical *Hub → Region → CH* is the same walk generalized to nested regions. Each region may carry an anchor lat/lng.
- **`hubs`** are *affiliations* (an institution like FHNW, an incubator/accelerator, a city scene). A hub **belongs to one region** (`hubs.region_id`) and carries its own lat/lng. A hub is a membership, **not** a radius.
- **`hub_memberships`** is entity↔hub (many-to-many), with `affiliation` (e.g. "FHNW-E-Mail", "Arbeitgeber") and `source` ('derived' | 'confirmed'). An entity may belong to **several** hubs — there is no forced primary hub (spec §14.3). Re-ranking weights every overlapping hub.
- **`entities.home_region_id`** anchors an entity to a region even when it has no hub membership (derived from location). This lets a region-scoped search include hub-less profiles.

### 5.2 Three axes, kept separate

| Axis | Question | Where it lives | Effect |
|---|---|---|---|
| **Hub / Region** (affiliation) | *where is someone anchored?* | `hub_memberships`, `entities.home_region_id` | **Hard filter** in search (with widening); re-rank + escalation weight in distribution |
| **Geographic proximity** | *how far away?* | `entities.lat/lng` vs hub/region anchor | **Soft** re-rank weight only |
| **Präsenzmodus** (Vor Ort / Hybrid / Remote) | *how is the work done?* | `anfragen.presence_mode` | gates the proximity/hub weights; "Remote" damps them |

"CH" is the top **region**, never a Präsenzmodus. On **Remote**, proximity and hub weights are automatically damped ("Remote — Hub/Nähe weniger relevant").

### 5.3 Resolving a scope selection → the hard filter

A search/Anfrage carries a **scope selection**: `all`, a `hub`, or a `region`. The app resolves it to two arrays passed into `hybrid_search`, then widens by re-resolving at the next level:

```text
resolveScope(selection, wideningLevel):
  if selection.kind == 'all':            return ( hub_ids=[], region_ids=[] )      # no filter
  if selection.kind == 'hub':
     hub = selection.hub
     case wideningLevel:
       HUB    -> ( [hub.id],            [] )                                       # strict hub
       REGION -> ( [],   subtree(hub.region_id) )                                  # widen to hub's region
       CH     -> ( [],   [] )                                                       # widen to all
  if selection.kind == 'region':
     case wideningLevel:
       REGION -> ( [],   subtree(selection.region_id) )
       CH     -> ( [],   [] )
```

`subtree(R)` = R plus all descendant regions (precomputed `region_closure`, or a recursive CTE, or `ltree`). An entity is **in scope** when it is a member of a scoped hub **or** its `home_region_id` is in the scoped region subtree. Empty/empty = unscoped ("Alle" doesn't filter).

The hard filter is applied **inside both retrieval CTEs, before each leg's `limit`** — filtering after the CTEs would let out-of-scope rows consume the limit and starve a small hub (FHNW might be ~200 of ~700k entities). See §6.2 for the exact SQL.

### 5.4 Widening: search vs distribution

- **Search (pull).** If the strict level returns too few in-scope results (below a floor `k_min`), widen one level (Hub → Region → CH) and re-run, surfacing a UI note ("zu wenige Treffer im Hub — auf Region erweitert"). Explicit and one-shot per step.
- **Distribution (push).** Widening is **time-driven by urgency** (§8): a *Dringend* Anfrage starts at the chosen level and widens automatically after 48 h of no contact.

---

## 6. Discovery & match pipeline — retrieve → re-rank → ground

```text
search(query_text, scope_selection, presence_mode, want_window, created_by):

  1. PREP
     q_emb   = embed("query: " + query_text)
     scope   = resolveScope(scope_selection, level=initial)         # §5.3
     anfrage = anfragen.insert(query_text, q_emb, scope_selection, presence_mode, want_window, status='offen')

  2. RETRIEVE  (§6.1 / 6.2) — HARD FILTER applied here
     SET LOCAL hnsw.iterative_scan = 'strict_order'
     ranked = hybrid_search(query_text, q_emb, scope.hub_ids, scope.region_ids, match_count=10)
     if len(ranked) < k_min: widen(scope); goto 2                    # §5.4

  3. EVIDENCE  (§6.2) — top winning chunks per entity for THIS query
     for e in ranked: e.evidence = topWinningChunks(e, q_emb, k=4)

  4. RE-RANK  (§6.3) — SOFT SIGNALS
     for e in ranked: e.final = rerank(e.base_rrf, anfrage, e)
     ranked = sort_desc(ranked, by=final)

  5. GENERATE (§6.4) — only for the entities actually shown (≤10)
     for e in ranked[:10]: out = geminiFlash(groundingPrompt(query_text, e, e.evidence))

  6. VALIDATE (§6.5)
     allowed    = {ev.id for ev in e.evidence}
     out.claims = [c for c in out.claims if set(c.source_ids) ⊆ allowed]
     chips      = [ev for ev in e.evidence if ev.id in citedIds(out.claims)]

  7. PERSIST + RENDER (§6.6)
     m = matches.upsert(anfrage.id, e.id, e.final, out.rationale, out.claims)
     match_citations.replaceAll(m.id, [chip.source_id for chip in chips])
     card = render(e, out.rationale, chips, availabilityPill, distance, orgLink)
```

### 6.1 Retrieval = keyword + semantic, fused by RRF (unchanged)

Two independent rankings over `chunks`: **keyword** (`fts @@ websearch_to_tsquery('german', q)`, ranked by `ts_rank_cd`) and **semantic** (`embedding <#> q_emb`, negative inner product, smaller = closer). Fused with **Reciprocal Rank Fusion** — each list contributes `weight / (rrf_k + rank)`, summed; `rrf_k = 50`. RRF combines rankings without needing the two score scales to be comparable. Each leg over-fetches `match_count * 4` rows.

### 6.2 The scoped `hybrid_search` (Stage 3 version)

The scope predicate is resolved into a `scoped` CTE and joined **inside both legs**, before each leg's `limit`. An entity qualifies if it is a member of a scoped hub **or** its `home_region_id` is in the scoped region subtree. Empty/empty arrays = unscoped (Stage 2 ships the same function without the `scoped` join).

```sql
create or replace function hybrid_search(
  query_text       text,
  query_embedding  vector(1024),
  scope_hub_ids    bigint[],            -- hubs in scope (empty = no hub constraint)
  scope_region_ids bigint[],            -- region subtree in scope (empty = no region constraint)
  match_count      int,
  full_text_weight float = 1,
  semantic_weight  float = 1,
  rrf_k            int   = 50
) returns table(entity_id bigint, score double precision)
language sql as $$
with scoped as (                                    -- entities allowed by the hard filter
  select e.id as entity_id
  from entities e
  where e.status = 'active'
    and (
      (cardinality(scope_hub_ids) = 0 and cardinality(scope_region_ids) = 0)        -- unscoped
      or e.home_region_id = any(scope_region_ids)
      or exists (select 1 from hub_memberships m
                 where m.entity_id = e.id and m.hub_id = any(scope_hub_ids))
    )
),
full_text as (
  select c.entity_id,
         row_number() over (
           order by ts_rank_cd(c.fts, websearch_to_tsquery('german', query_text)) desc
         ) as rank_ix
  from chunks c
  join scoped sc on sc.entity_id = c.entity_id
  where c.fts @@ websearch_to_tsquery('german', query_text)
  limit least(match_count, 30) * 4
),
semantic as (
  select c.entity_id,
         row_number() over (order by c.embedding <#> query_embedding) as rank_ix
  from chunks c
  join scoped sc on sc.entity_id = c.entity_id
  limit least(match_count, 30) * 4
),
fused as (
  select coalesce(f.entity_id, s.entity_id) as entity_id,
         coalesce(1.0/(rrf_k + f.rank_ix), 0.0) * full_text_weight +
         coalesce(1.0/(rrf_k + s.rank_ix), 0.0) * semantic_weight as score
  from full_text f
  full outer join semantic s on f.entity_id = s.entity_id
)
select entity_id, max(score) as score              -- MAX rollup: best signal per entity
from fused
group by entity_id
order by score desc
limit least(match_count, 30);
$$;
```

**Rollup choice** (unchanged): `max(score)` = "the entity with the single strongest matching chunk wins." `sum(score)` rewards breadth instead — a tunable, A/B-able thesis point.

**Capturing evidence** (unchanged): for each shown entity fetch its top-k chunks for *this* query (closest by `<#>`), dedup by `source_id` so one source = one chip, assign stable ids `S1, S2, …`.

### 6.3 Re-ranking — the soft signals (NEW)

Retrieval gives a relevance base (`base_rrf`). v2 adds an explicit re-rank over the shown candidates. **Additive boosts on the normalized base** (easy to explain, easy to A/B):

```
final = base_rrf_norm
      + W_avail   * availabilityOverlap(want_window, entity.availability)   # 0..1, neutral 0.5 if unset
      + W_present * presenceAlignment(anfrage.presence_mode, entity)        # 0..1
      + W_geo     * proximity(anchor, entity.latlng)                        # 0..1, gated by presence
      + W_hub     * hubOverlap(anfrage.scope, entity.hubs)                  # 0..1
      + W_open    * (entity.open_to_requests ? 1 : 0)
```

- **availabilityOverlap** — fraction of the requested `[want_from, want_to]` covered by the entity's `[available_from, available_to]`, scaled by Pensum compatibility (`min(pensum, want_pensum)/want_pensum`). **No window set → 0.5 (neutral, never negative)** per spec §6.
- **presenceAlignment** — `remote` query ⇒ any entity aligns (1.0). `onsite`/`hybrid` ⇒ rewards entities anchored near the scope.
- **proximity** — haversine(entity, anchor) mapped through a decay; **gated by presence**: on `remote`, `W_geo` and `W_hub` collapse to ~0 (the spec's Remote-Entschärfung).
- **hubOverlap** — count of entity hubs that intersect the scoped hub/region, normalized.
- **W_open** — the "Offen für Anfragen" toggle nudges open profiles up (spec §4.2).

Weights are config, A/B-able, and a thesis evaluation axis alongside RRF weights and rollup. **Time and presence never hard-filter here** — they only reorder (§7).

### 6.4 Grounded generation (Gemini Flash) — unchanged

Generate **only for shown entities** (≤10). The model sees the query, a thin entity profile, and the winning chunks — never the full entity. Low temperature, JSON-constrained via `responseSchema`.

```jsonc
// system: "Du bist ein Matching-Assistent. Nutze AUSSCHLIESSLICH die bereitgestellten Belege.
//          Mache keine Aussage, die nicht durch die Belege gestützt ist. Zitiere je Aussage die
//          source_id(s). Antworte NUR als JSON. Sprache: Deutsch."
// user:
{
  "anfrage": "Suche Embedded-Team mit Zephyr + CI für Hardware-Tests",
  "entity":  { "name": "David Aregger", "role": "CTO & Mitgründer", "org": "Sentio Robotics GmbH, Baden" },
  "evidence": [
    { "id": "S1", "type": "orcid_pub",    "label": "«HIL-Testing at Scale» (Embedded World 2025)", "text": "…" },
    { "id": "S2", "type": "github",       "label": "github.com/sentio-robotics/zephyr-ci",          "text": "…" },
    { "id": "S3", "type": "company_site", "label": "sentio-robotics.ch",                            "text": "…" }
  ]
}
```

```jsonc
// responseSchema
{ "type":"object",
  "properties": {
    "rationale": {"type":"string"},                                  // German, 1–3 sentences
    "claims": {"type":"array","items":{"type":"object",
      "properties":{"text":{"type":"string"},
                    "source_ids":{"type":"array","items":{"type":"string"}}},
      "required":["text","source_ids"]}},
    "cited_source_ids": {"type":"array","items":{"type":"string"}}
  },
  "required":["rationale","claims","cited_source_ids"] }
```

### 6.5 Citation validation (non-negotiable) — unchanged

```java
Set<String> allowed = evidence.stream().map(Evidence::id).collect(toSet());
result.claims().removeIf(c -> !allowed.containsAll(c.sourceIds()));      // drop hallucinated claims
Set<String> cited = result.claims().stream()
    .flatMap(c -> c.sourceIds().stream()).collect(toSet());              // recompute (don't trust model's list)
List<Evidence> chips = evidence.stream().filter(e -> cited.contains(e.id())).toList();
if (chips.isEmpty()) return Optional.empty();                           // no grounded claim → drop entity
```

`chips` → `match_citations`; `rationale` + `claims` → `matches`. A citation we didn't pass can never reach a card.

### 6.6 Render payload

```jsonc
{
  "entity": { "name": "David Aregger", "headline": "CTO & Mitgründer", "org": "Sentio Robotics GmbH" },
  "org_link": { "name": "Sentio Robotics GmbH", "hr_verified": true, "url": "/org/…" },   // §10
  "availability": "60 %, Aug–Okt",                       // structured pill (§6.3 / spec §6)
  "presence": "hybrid",
  "distance": "8 Min. von FHNW Brugg",                   // computed at query time, not stored
  "rationale": "Davids Startup baut Inspektionsroboter …",
  "chips": [ { "label": "«HIL-Testing at Scale» (Embedded World 2025)", "url": "…", "type": "orcid_pub" },
             { "label": "github.com/sentio-robotics/zephyr-ci", "url": "…", "type": "github" } ]
}
```

`distance` is computed at query time (haversine entity↔anchor, optionally a routing API for "minutes"); it is not stored on the match.

---

## 7. Hard vs soft filters (the filter reference)

The one place that states, unambiguously, what **excludes** a result vs what only **reorders** it.

**Hard filters — a non-match is removed from the result set:**

1. **Scope (Hub / Region / CH)** — §5. With explicit widening Hub → Region → CH. "Alle" applies no filter.
2. **`entities.status = 'active'`** — soft-deleted / inactive entities never appear.
3. **Grounding** — an entity whose claims all fail citation validation is dropped (§6.5). (A filter on *output*, but it does remove the row.)

**Soft signals — never remove a result, only change its order (§6.3):**

- Availability window overlap (Pensum-scaled; unset = neutral).
- Präsenzmodus alignment (and the Remote damping of geo/hub).
- Geographic proximity.
- Hub overlap count.
- "Offen für Anfragen" boost.
- The search-side **Zeitfilter** (spec §7) — explicitly *re-ranking, not a hard filter*: "Leer lassen = keine Zeit-Einschränkung. Beeinflusst die Reihenfolge, filtert nicht hart."

Rule of thumb: **affiliation and existence are hard; everything about *fit* is soft.** Presence "Remote" is **not** a hard filter — it damps geo/hub weights to ~0 rather than excluding nearby people.

---

## 8. Anfrage lifecycle & distribution

### 8.1 Lifecycle: Offen → Verteilt → Kontaktiert → Geschlossen

`anfragen.status` is a 4-state machine (spec §4.1):

- **`offen`** — live and publicly readable on the Anfrage-Board.
- **`verteilt`** — distribution ran; matching profiles + tag-subscribers were notified. The Anfrage shows "X Profile … benachrichtigt"; recipients see "Verteilt, weil dein Profil die Tags … trägt" (the reason comes from `anfrage_distributions`).
- **`kontaktiert`** — at least one `contact_request` was brokered for this Anfrage.
- **`geschlossen`** — owner marked it solved; removed from the board (still visible to the owner).

A conceptual **Empfehlung** stage stays concept-only; the rule-based Hub→Region widening below is its concrete, visible precursor.

### 8.2 Distribution is rule-based on tags + scope (not semantic)

This is the key architectural split: **search is semantic, distribution is tag-based.** Push must be explainable ("warum wurde ich benachrichtigt"), so it matches on tags and scope, not embeddings.

```text
distribute(anfrage, wideningLevel = initialFor(anfrage.scope)):
  scope   = resolveScope(anfrage.scope, wideningLevel)                  # §5.3 — same walk as search
  byTags  = entities matching anfrage.tags via entity_tags              # reason='profile_match'
  bySubs  = entities subscribed to any anfrage.tag via tag_subscriptions   # reason='subscription'
  cohort  = (byTags ∪ bySubs) filtered to in-scope (scope)              # hard scope, like search
  for e in cohort:
      anfrage_distributions.upsert(anfrage.id, e.id,
          reason, matched_tags = anfrage.tags ∩ e.tags,
          widening_level = wideningLevel, notified_at = now())
      notify(e)
  anfrage.status = 'verteilt'
```

### 8.3 Urgency drives escalation (spec §8 / §9)

`anfragen.urgency ∈ {flexibel, bald, dringend}` + optional `deadline`. The strategy is shown to the creator transparently ("So verteilen wir deine Anfrage").

| Urgency | Strategy | Scheduler |
|---|---|---|
| **Dringend** | notify all matching profiles + subscribers in the **tightest** level; after **48 h** with no `contact_request` → widen one level (Hub → Region → CH) and re-distribute | `distribute(...)` then a 48h delayed job per level |
| **Bald** | notify in the chosen hub/region; **5-day** reminder to non-responding top matches | one reminder job |
| **Flexibel** | notify matching profiles + subscribers; **no escalation**; stays resting on the board | none |
| **+ Deadline** | as per urgency, plus a final wave **~24 h before** the deadline | extra job |
| **Deadline passed** | card flips to "Frist abgelaufen", drops out of default sort (still owner-visible) | guard at read time |

Widening here is **regelbasiert** — no intelligent routing (that stays concept-only). It reuses the exact same `resolveScope` walk as search (§5.3), so "tightest level" and "widen one level" mean the same thing on both paths.

---

## 9. Tags, skills & subscriptions

Tags are first-class in v2 because they carry the **Kompetenzen** display, the **distribution** match, and the **subscription** feed.

- **`tags`** — a controlled vocabulary (`slug`, `label`). Keep it controlled, not free-text-per-row, so subscriptions and distribution join cleanly; new tags are minted on demand but normalized to a slug.
- **`entity_tags`** — an entity's skills/Kompetenzen. `origin ∈ {derived, manual}`: derived from sources during ingestion (§4.2 step 7), editable by the user ("Tags werden aus deinen Quellen abgeleitet — du kannst sie ergänzen oder entfernen"). Manual edits are sticky across re-harvests.
- **`anfrage_tags`** — the tags an Anfrage carries; they steer distribution.
- **`tag_subscriptions`** — "Abonnierte Themen": entity↔tag; a subscriber is notified when a matching Anfrage is distributed even if their profile wasn't a direct skill match.

Tags are **not** the semantic retrieval mechanism — chunks + embeddings are. Tags are the **explainable push + filter** layer. Keep the two from collapsing into one.

---

## 10. Identity, accounts & Person↔Org links

- **`accounts`** — login + **verified email**, owns exactly one `entity` (the claimed profile). Most entities are harvested and have **no** account; an account appears when a person builds/claims their profile. `anfragen.created_by` and `contact_requests.from_entity_id` require an account (search is account-free; **Anfrage and contact require login** — spec §4.1).
- **`entity_links`** — Person↔Organisation (`person_entity_id`, `org_entity_id`, `role`, `relation_type ∈ {employee, self_employed, founder, …}`, `hr_verified`, `public`). Rendered on both profiles, in search results, on Anfrage cards, and in the contact handoff so it's always clear *who from where* is acting (spec §12).
- **`entities.affiliation_kind ∈ {company, self_employed, private}`** — Firma / Selbständig / Privatperson. A person is never shown without an origin; "Privatperson"/"Selbständig" are valid origins.
- **`entities.hr_verified`** — Organizations matched against Handelsregister/Zefix carry the **HR-verifiziert** badge (set during `zefix` ingestion, §4.1). Person↔Org links to a verified org inherit verification; private-person links are self-declared.

---

## 11. Contact handoff (sender context, gating)

The platform's role **ends at first contact** — it's a one-time handoff to email, not an inbox (spec §11.3).

- **`contact_requests`** — `from_entity_id`, `to_entity_id`, `anfrage_id` (nullable: set when initiated from a board Anfrage), `message` (non-empty), `sender_context jsonb`, `created_at`. Creating one moves the related Anfrage to **`kontaktiert`**.
- **Sender context** (jsonb snapshot, frozen at send time so later profile edits don't rewrite history): `{ name, role, org, short_description, searched_text }`. The recipient's notification/email shows *who fragt*, a 1–2 sentence description derived from the sender's source, the linked org, *what was searched*, the personal message, and the sender's email to continue off-platform (spec §11.2).
- **Gating (§11.1)** — to send, the sender needs: signed-in profile + **verified email**; an **affiliation** (company/self/private); **≥1 source** (LinkedIn / Website / `manual_text`); a **non-empty message**. *Not* required: full source flow, active availability, complete skill profile. If the base profile is insufficient, the dialog shows inline what's missing with a "Profil vervollständigen" link — the button is **not** silently disabled.

---

## 12. End-to-end trace (worked example)

Query *"Suche Embedded-Team mit Zephyr + CI für Hardware-Tests"*, scope = **Hub: FHNW**, Präsenzmodus = Hybrid, want_window = Aug–Okt @ 60 %.

1. **Ingestion (earlier).** Sentio's `zephyr-ci` repo, David's Embedded World 2025 paper (ORCID→OpenAlex), and `sentio-robotics.ch` ingested → one entity *David Aregger* via `github_login`, `orcid`, `domain`. Chunked + embedded (`passage:`). ORCID affiliation derived an **FHNW hub_membership** (confirmed); `home_region_id = Region Baden`. Tags `{Embedded, Zephyr, HIL-Testing}` derived.
2. **Scope resolve.** Selection `hub:FHNW`, level HUB → `scope_hub_ids=[FHNW]`, `scope_region_ids=[]`.
3. **Retrieve (hard filter).** Query embeds with `query:`. Scoped `hybrid_search` keeps only FHNW-affiliated entities; README chunk (Zephyr CI) + paper abstract rank high; `max` rollup gives David a strong `base_rrf`.
4. **Evidence.** Top chunks → `S1` (paper), `S2` (repo), `S3` (site).
5. **Re-rank.** availabilityOverlap(Aug–Okt 60 %) high; presence Hybrid → proximity counts; FHNW hub overlap +; open_to_requests +. `final` lifts David.
6. **Generate.** Gemini Flash returns a German rationale citing `S1, S2`.
7. **Validate.** `S1,S2` ∈ allowed → kept; `S3` not cited → not a chip.
8. **Render.** Card: rationale, chips (paper + repo), org-link "Sentio Robotics GmbH · HR-verifiziert", "60 %, Aug–Okt", "8 Min. von FHNW Brugg".

Had the query returned `< k_min` FHNW matches, step 3 would widen to **Region Baden**, then **CH** (§5.4). Had this been a *distributed Anfrage* instead of a live search, the cohort would be tag-based (`{Embedded, Zephyr}`) within FHNW, widening on 48 h silence if *Dringend* (§8).

---

## 13. JPA / monolith implementation notes

Plain monolith → normal JPA associations (`@ManyToOne`/`@OneToMany`) across all tables. No module-boundary workarounds.

- **`vector(1024)`** → `hibernate-vector` (Hibernate 6.4+). Don't store as `String`.
- **`fts tsvector`** → DB-generated; map read-only (`@Column(insertable=false, updatable=false)`); never write from JPA.
- **Array columns** (`preferred_collaboration text[]`, `languages text[]`, `matched_tags text[]`) and **`claims jsonb`**, **`sender_context jsonb`** → explicit Hibernate array / JSON type mappings; they don't default. (The scope arrays are function *parameters*, not stored columns.)
- **Join tables with attributes** (`hub_memberships.affiliation/source`, `entity_tags.origin`, `anfrage_distributions.*`) → model as their own `@Entity` with `@EmbeddedId`, **not** `@ManyToMany`.
- **`entity_links`** has two FKs to `entities` (person + org) → two distinct `@ManyToOne` to the same target; name the join columns explicitly.
- **`regions` self-reference** (`parent_region_id`) → `@ManyToOne` to `Region`; resolve subtrees with a recursive CTE / closure table, not eager fetch.
- **`accounts.entity_id`** is one-to-one to the claimed entity → `@OneToOne`.
- **Schema authority** → the per-stage Flyway migrations are the source of truth. `ddl-auto: validate`. Hibernate won't generate HNSW indexes, generated `tsvector` columns, or the `hybrid_search` function.

---

## 14. Schema

Grouped by domain. Each table is shown in its consolidated form. Where a later section adds columns to a table, that is noted inline rather than repeated as a separate migration.

### 3.1 Core: entity → source → chunk

The searchable substrate. Every profile is an `entity`; each entity has one or more `sources`; each source is split into `chunks` that carry the embedding and full-text vectors. This is the only part of the schema the retrieval pipeline reads.

```sql
create extension if not exists vector;

create table entities (
  id bigint primary key generated always as identity,
  entity_type text not null,
  display_name text not null,
  headline text,
  org_name text,
  home_region_id bigint references regions(id),   -- resolved during ingestion (§3.2)
  latitude double precision,
  longitude double precision,
  status text not null default 'active',
  metadata jsonb default '{}',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index on entities (home_region_id);
```

> `entities` is extended in later sections with structured-availability columns (`open_to_requests`, `available_from` / `available_to`, `pensum_pct`, `availability_note`, `preferred_collaboration[]`, `languages[]`) and affiliation columns (`affiliation_kind`, `hr_verified`). Those columns are documented where the features that use them are defined.

```sql
create table entity_identifiers (
  id bigint primary key generated always as identity,
  entity_id bigint not null references entities(id) on delete cascade,
  id_type text not null,
  id_value text not null,
  unique (id_type, id_value)
);

create table sources (
  id bigint primary key generated always as identity,
  entity_id bigint not null references entities(id) on delete cascade,
  source_type text not null,
  source_url text,
  label text,
  provenance text not null default 'harvested',
  consent_basis text,
  raw_content text,
  content_hash text,
  fetch_status text default 'ok',
  fetched_at timestamptz,
  created_at timestamptz not null default now(),
  unique (source_url)
);
create index on sources (entity_id);

create table chunks (
  id bigint primary key generated always as identity,
  source_id bigint not null references sources(id) on delete cascade,
  entity_id bigint not null references entities(id),   -- denormalized for scoped filtering
  chunk_index int not null,
  content text not null,
  section text,
  token_count int,
  fts tsvector generated always as (to_tsvector('german', content)) stored,
  embedding vector(1024),
  embedding_model text default 'multilingual-e5-large',
  embedded_at timestamptz,
  created_at timestamptz not null default now()
);
create index on chunks using gin  (fts);
create index on chunks using hnsw (embedding vector_ip_ops);
create index on chunks (entity_id);
create index on chunks (source_id);
```

Key invariants: `sources.source_url` is unique (idempotent re-ingestion keys off it via `content_hash`); `chunks.entity_id` is denormalized from `sources` so the scoped search can filter without a join; `fts` is a stored generated column so keyword search never recomputes it.

### 3.2 Geo & scope

The `regions` tree (CH at the root, `parent_region_id` null) plus the `hubs` that belong to regions and the `hub_memberships` linking entities to hubs. This is what backs the Hub → Region → CH hard filter.

```sql
create table regions (
  id bigint primary key generated always as identity,
  slug text not null unique,
  name text not null,
  parent_region_id bigint references regions(id),   -- null = root (CH)
  latitude double precision,
  longitude double precision,
  created_at timestamptz not null default now()
);

create table hubs (
  id bigint primary key generated always as identity,
  slug text not null unique,
  name text not null,
  hub_type text,
  region_id bigint not null references regions(id),
  latitude double precision,
  longitude double precision,
  created_at timestamptz not null default now()
);
create index on hubs (region_id);

create table hub_memberships (
  entity_id bigint not null references entities(id) on delete cascade,
  hub_id bigint not null references hubs(id) on delete cascade,
  affiliation text,
  source text not null default 'derived',   -- 'derived' | 'confirmed'
  joined_at timestamptz not null default now(),
  primary key (entity_id, hub_id)
);
create index on hub_memberships (hub_id);
```

> A scoped `hybrid_search(..., scope_hub_ids, scope_region_ids)` reads these tables; its definition lives in the discovery-pipeline section (§6). An optional materialized `region_closure(ancestor_id, descendant_id)` can be added for fast subtree lookups. Ingestion resolves each entity's `home_region_id` and writes a `hub_membership(source='derived')`.

### 3.3 Requests & matching

An `anfrage` is a demand-side query; each produces `matches` (one row per candidate entity, base and re-ranked score plus grounded rationale), and each match records which `sources` it cited.

```sql
create table anfragen (
  id bigint primary key generated always as identity,
  created_by bigint,                                  -- → accounts(id), see identity section
  title text,
  description text,
  query_text text not null,
  query_embedding vector(1024),
  presence_mode text not null default 'onsite',       -- 'onsite' | 'hybrid' | 'remote'
  scope_kind text not null default 'all',             -- 'all' | 'hub' | 'region'
  scope_hub_id bigint references hubs(id),
  scope_region_id bigint references regions(id),
  want_from date,
  want_to date,
  want_pensum_pct int,
  collaboration_type text,
  status text not null default 'offen',               -- 'offen'|'verteilt'|'kontaktiert'|'geschlossen'
  created_at timestamptz not null default now()
);
```

> `anfragen` is extended in the distribution section with `urgency`, `deadline`, and `distributed_at`, and `created_by` is later constrained by a foreign key to `accounts(id)`.

```sql
create table matches (
  id bigint primary key generated always as identity,
  anfrage_id bigint not null references anfragen(id) on delete cascade,
  entity_id bigint not null references entities(id) on delete cascade,
  base_score double precision,   -- base RRF score
  score double precision,        -- final score after re-rank
  rationale text,
  claims jsonb,
  created_at timestamptz not null default now(),
  unique (anfrage_id, entity_id)
);

create table match_citations (
  match_id bigint not null references matches(id) on delete cascade,
  source_id bigint not null references sources(id) on delete cascade,
  primary key (match_id, source_id)
);
```
Key invariant: a row in `match_citations` can only reference a source that was actually retrieved for that match. Citation validation enforces this before persistence, so a chip on a card always resolves to real evidence.

---
## 14. Open decisions

- **`region_closure` vs recursive CTE vs `ltree`** — for subtree resolution (§5.3). Closure table is simplest to JOIN; the region tree is shallow, so a recursive CTE is fine too. Decide before Stage 3.
- **`claims` jsonb vs normalized `match_claims`** — jsonb maps to generator output; normalize only if you need to query individual claims.
- **Distribution match = tags only vs tags + semantic** — v2 chooses **tags only** for explainability (§8.2). If recall suffers, a semantic second pass could augment the cohort — but keep the *reason string* tag-derived.
- **Default hub/region policy** — Stage 3 needs every searchable entity reachable by *some* scope. Region anchor (`home_region_id`) covers hub-less entities; decide whether a catch-all hub is still wanted.
- **Soft scope as alternative to hard** — `hub_memberships` + `home_region_id` also support a future soft-boost (RRF/re-rank bonus instead of `WHERE`) if the hard filter proves too aggressive on a sparse hub.
- **Account ↔ entity claiming** — how a signed-up user claims a pre-existing harvested entity (id match? manual link?) without creating a duplicate.
