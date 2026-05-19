# v8 — Source-Ingested, Evidence-Anchored CLI

This is the next prototype iteration. It throws away the JSONL-of-hand-written-bios approach from v1–v7 and proves the **actual** BadenConnect data flow:

> Real public sources → structured documents → embedded → hybrid-retrieved → LLM rationale **with citations back to those documents**.

If v8 works end-to-end on the CLI, the Spring Boot + React implementation becomes plumbing. If we go straight to Spring Boot first, we burn weeks on REST/JPA wiring before discovering that LinkedIn is hostile or that the LLM hallucinates citations.

---

## Why this iteration exists

The design files (`v0.4-prototype` mockups from Claude Design) commit us to a specific UX contract:

- Every match has a **"Warum diese Person?"** block with **numbered footnotes** pointing to concrete artifacts — an Inserat, a GitHub repo, a blog post, a publication.
- Profiles are **synthesized from public sources**, not authored. There is no "Schreib deine Bio"-Feld.
- **Geographic nearness** (FHNW → Aargau → CH) is a re-ranking weight, not a hard filter.

None of v1–v7 prove this. They embed pre-written paragraphs and ask the LLM to rationalize — the citations in v7 are inferred from prose, not anchored to documents. v8 fixes the foundation.

---

## What's in scope for v8

**Sources (in priority order):**

1. **GitHub** — username → profile bio, top repos, READMEs, languages, recent activity
2. **Personal Website** — URL → fetch, extract main text (Readability-style)
3. **LinkedIn CV PDF export** — user uploads the PDF that LinkedIn lets you download; we extract text. (Public LinkedIn scraping is hostile and ToS-violating. Don't go there.)
4. *(stretch)* **OpenAlex** — ORCID / name → publications
5. *(stretch)* **IRF FHNW (OAI-PMH)** — institutional repo lookup

**Entity types:** `person`, `company`, `inserat`. v8 implements `person` fully and stubs the other two (one fake inserat, one fake company) so we can prove cross-entity citation works (Marc's *Inserat* shows up as evidence for *Marc the person*).

**Out of scope for v8:** UI, OAuth, real Handelsregister/LINDAS ingestion, Spring Boot, React, multi-user, auth, persistence beyond local Postgres.

---

## The data model (this is the load-bearing decision)

Two tables for the search core, plus one for source provenance.

### `entities`

| col | type | notes |
|---|---|---|
| `id`              | text PK         | stable slug, e.g. `per-marc-hofer`, `ins-marc-secrev`, `firm-trafosoft` |
| `type`            | text            | `person` \| `company` \| `inserat` |
| `name`            | text            | display name |
| `canonical_location` | text         | "Baden", "Brugg", "FHNW Brugg-Windisch" — free text for now |
| `scope`           | text            | `fhnw` \| `aargau` \| `ch` — drives re-rank weight |
| `created_at`      | timestamptz     | |
| `summary`         | text            | LLM-synthesized 2-3 sentence bio, regenerated when documents change. Used **only** for the snippet in result cards. Never fed back into retrieval. |

### `documents`

The unit of retrieval. One entity has N documents. Each document is one source artifact (a repo, a blog post, a publication, the Inserat body, a CV section).

| col | type | notes |
|---|---|---|
| `id`           | text PK    | stable, e.g. `gh-mhofer-oauth21-migration`, `web-marchofer-blog-oauth-playbook`, `cv-marc-section-experience` |
| `entity_id`    | text FK    | → entities.id |
| `source_type`  | text       | `github_repo` \| `github_bio` \| `website_page` \| `linkedin_cv` \| `inserat_body` \| `publication` |
| `source_url`   | text       | the original URL (or `linkedin-cv://uploaded/marc-cv.pdf` for uploads) |
| `source_label` | text       | human-readable label for the citation: "GitHub repo · oauth21-migration", "Helvetia Tech Blog · März 2025" |
| `fetched_at`   | timestamptz| |
| `text`         | text       | the cleaned content that gets embedded |
| `embedding`    | vector(1024) | multilingual-e5-large, `passage:` prefix |
| `tsv`          | tsvector   | generated, `simple` dictionary, GIN index |
| `meta`         | jsonb      | source-specific: `{stars, language, published_at, ...}` |

### `ingestion_runs` *(optional, nice-to-have)*

Audit trail so we can re-run ingestion idempotently.

| col | type |
|---|---|
| `id` serial PK, `entity_id` FK, `source` text, `started_at`, `finished_at`, `status`, `error` |

### Why this split matters

- **One entity, many documents** lets the LLM cite *which* source it relied on (`[gh-mhofer-oauth21-migration]`) instead of hand-waving over a blob.
- **Retrieval is on documents, then dedup-by-entity** — Marc's `oauth21-migration` repo and his `OAuth 2.1 Playbook` blog post both hit the query; we count him once, but we *keep both document IDs* as evidence.
- **Inserate are entities** — they have a lifecycle (gültig bis, owner reactions) but also act as **first-class citation targets** for *their owner's* search results. That's the "Marc has an active Inserat for exactly this" pattern from the mockup.
- **`summary` on the entity is a UI convenience, not a retrieval input.** The retrieval input is always the document corpus. This prevents the "LLM-generated bio drifts from sources" problem.

---

## The ingestion pipeline

One script per source, all writing into the same `documents` table. Each is idempotent on `(entity_id, source_type, source_url)`.

### `08a_ingest_github.js`

Input: GitHub username. Uses unauthenticated REST API (60 req/h is enough for prototype; bump with a PAT in `.env` if needed).

Produces documents:
- `github_bio` — one doc with `text = "{display_name}. {bio}. Location: {location}. Followers: {n}."`
- `github_repo` (one per top repo, max ~10 by stars) — `text = "{name} — {description}. README excerpt: {first 1500 chars of README}. Primary language: {lang}. Topics: {topics}."`

`meta`: `{stars, forks, language, topics, pushed_at}`

### `08b_ingest_website.js`

Input: URL. Fetch HTML, run through `@mozilla/readability` + `jsdom` (or `cheerio` + a hand-rolled heuristic) to extract main content. Split into per-page documents if the site has multiple pages (start with just the landing page + any `/about`-ish and `/blog/*` it links to, max 5 pages).

`source_type`: `website_page`
`text`: extracted main text, capped at ~4000 chars per page
`meta`: `{title, og_description, h1}`

### `08c_ingest_linkedin_cv.js`

Input: PDF path. Use `pdf-parse` to extract text. Don't try to parse structure — just chunk by detected section headers (`Experience`, `Education`, `Skills`, etc., case-insensitive, multilingual) and emit one document per section.

`source_type`: `linkedin_cv`
`source_url`: `linkedin-cv://uploaded/{filename}`
`text`: section content
`source_label`: e.g. `"LinkedIn CV · Experience"`

### `08d_ingest_inserat.js`

For the prototype: hand-write 2-3 Inserate as JSON files, this script reads them and creates one `inserat` entity + one `inserat_body` document per file. Real Inserate creation will come from the UI later — this script just seeds the corpus.

```js
// inserate/ins-marc-secrev.json
{
  id: "ins-marc-secrev",
  owner_entity_id: "per-marc-hofer",
  title: "4h/Woche Security Review für OAuth/JWT-Flows",
  body: "...",
  scope: "aargau",
  valid_until: "2026-08-01",
  honorar_mode: "chf",
}
```

`owner_entity_id` is **critical** — it's what makes Marc's Inserat show up as evidence on Marc's person card. We may want a separate `entity_links(from_id, to_id, kind)` table for this, but for v8 just putting it in `documents.meta` is fine.

### `08e_summarize_entity.js`

After all sources for an entity are ingested, call gemma4 with all the entity's documents and ask for a 2-3 sentence German summary. Write to `entities.summary`. This is the snippet that appears under the name in result cards.

Important: **the summary is not embedded and not retrieved against.** It's pure UI cosmetics. Retrieval always runs against `documents`.

---

## The search pipeline

Same shape as v7, but operating on documents and producing evidence-anchored results.

```
query
  ↓
embed (query: prefix, e5-large)
  ↓
parallel:
  ├─ dense:    ORDER BY embedding <=> $1 LIMIT 100  (on documents)
  └─ lexical:  WHERE tsv @@ to_tsquery('simple', ...) ORDER BY ts_rank_cd LIMIT 100
  ↓
RRF fuse (k=60) at the DOCUMENT level
  ↓
group by entity_id — sum doc-level RRF scores, keep top 3 documents per entity as "evidence"
  ↓
scope re-rank — multiply entity score by {fhnw: 1.3, aargau: 1.15, ch: 1.0}
  ↓
take top 5 entities
  ↓
for each entity, call LLM with:
    - the query
    - the entity's name, type, scope
    - the entity's 3 best-matching documents (id + source_label + text)
    - explicit instruction to cite documents by id
```

### The rationale prompt

```
Du analysierst, ob diese Entität zur Suchanfrage passt.

Suchanfrage:
"{query}"

Entität:
{type} · {name} · {scope}

Verfügbare Dokumente (du DARFST und SOLLST diese per ID zitieren):
[1] id={doc1_id} · {doc1.source_label}
    {doc1.text}

[2] id={doc2_id} · {doc2.source_label}
    {doc2.text}

[3] id={doc3_id} · {doc3.source_label}
    {doc3.text}

Antworte als JSON, exakt dieses Schema:
{
  "rolle": "max 6 Wörter",
  "kontext": "Firma/Studium/Selbstständig, max 6 Wörter",
  "skill_tags": ["max 3 konkrete Tags zur Anfrage"],
  "warum": "2 Sätze. Belege MÜSSEN per [doc_id] zitiert werden, z.B. 'Marc hat vor 3 Tagen ein Inserat veröffentlicht [ins-marc-secrev]'.",
  "vorbehalte": "1 Satz oder null",
  "evidence": ["doc_id", "doc_id", ...] // die tatsächlich zitierten IDs, in Reihenfolge des Auftretens
}

Wichtig: erfinde KEINE Dokument-IDs. Nur die oben aufgeführten sind erlaubt.
```

After receiving the JSON, **validate the cited IDs** — strip any the LLM made up. If `evidence` is empty (LLM didn't cite anything), this is a smell — log it and fall back to "no rationale available" rather than rendering bullshit.

### The output

The CLI renders each match exactly like the mockup:

```
1   [MH]  Marc Hofer   • verifiziert
         Senior Security Engineer · Helvetia Versicherungen · Basel · ⌖ Baden
         [OAuth 2.1]  [JWT]  [AuthZ-Design]

         ▼ Warum diese Person?
         Marc Hofer hat vor 3 Tagen ein Inserat veröffentlicht, das 4h/Woche
         externe Security-Reviews für OAuth/JWT-Flows anbietet¹. Sein Profil
         zitiert mehrere Beiträge zu OAuth 2.1 und JWT-Härtung in regulierten
         Domänen².

         EVIDENZ
         ¹  Inserat — Security-Review (3 Tage alt)  · ins-marc-secrev
         ²  github.com/mhofer/oauth21-migration       · Repo · 312★
         ³  OAuth 2.1 Playbook · Helvetia Tech Blog  · März 2025
```

The footnote rendering is mechanical: find `[doc_id]` in `warum`, replace with superscript `¹²³` in order of appearance, then list the cited documents below with their `source_label` + `source_url`.

---

## File layout

```
prototyping/industryProfiles/
├── 08_plan.md                       ← this file
├── 08_schema.sql                    ← entities, documents, indexes
├── 08a_ingest_github.js
├── 08b_ingest_website.js
├── 08c_ingest_linkedin_cv.js
├── 08d_ingest_inserat.js
├── 08e_summarize_entity.js
├── 08_search_cli.js                 ← the REPL, replaces 07
├── seeds/
│   ├── people.json                  ← {id, name, github, website, cv_pdf, scope, location}
│   └── inserate/
│       ├── ins-marc-secrev.json
│       └── ins-trafosoft-sensorfusion.json
└── cache/                           ← cached HTTP responses so re-runs are free
```

`seeds/people.json` is the master list. Run `node 08_ingest_all.js` (a thin wrapper) to ingest everyone end-to-end.

---

## Build order

Do it in this order. **Don't** parallelize — each step de-risks the next.

1. **`08_schema.sql`** — write and apply. Verify with a hand-inserted entity + doc.
2. **`08a_ingest_github.js`** for your own GitHub. Verify the documents look sane (good text, no garbage HTML).
3. **`08_search_cli.js`** — just dense retrieval on documents, dedup-by-entity, no LLM yet. Verify the right *documents* surface for a few queries.
4. **`08b_ingest_website.js`** for your own site. Confirm Readability extraction works (some sites will be hostile — that's data, note it).
5. **`08c_ingest_linkedin_cv.js`** — export your own LinkedIn CV, ingest, verify sections.
6. **`08d_ingest_inserat.js`** — seed one Inserat for yourself. Confirm the Inserat document shows up in search results when querying your specialty.
7. **Add lexical leg + RRF** to `08_search_cli.js`. Compare top-K with/without.
8. **Add scope re-rank.** Verify FHNW-scoped entities float up on equal evidence.
9. **Add LLM rationale with `[doc_id]` citations.** Validate the cited IDs against the candidate set. This is the moment of truth.
10. **`08e_summarize_entity.js`** — generate the snippets that go under the name.
11. **Render footnotes** in the CLI output exactly as the mockup shows.
12. Ingest the other 3 real people (Luca, Nicolas, Andrei) + 1-2 fake companies + 2-3 fake Inserate, run the demo queries from the design.

Stop here. **Do not start Spring Boot until v8's demo queries produce results that match the design mockup quality.**

---

## Decisions to make before starting

- **GitHub auth:** use a PAT (5000 req/h) or stick to unauthenticated (60 req/h)? PAT is friendlier for re-runs; put it in `.env` and `.gitignore` it.
- **Website fetching politeness:** set a real User-Agent (`BadenConnect-Prototype/0.1 (contact@...)`), respect `robots.txt`, cache responses in `cache/` so re-runs are free.
- **LinkedIn:** confirm the user uploads the *LinkedIn data export* (the ZIP/PDF they request from LinkedIn settings), not a screen-scrape. There is no API path that's not hostile.
- **Document size limit:** cap each document at ~4000 chars before embedding. e5-large has a 512-token context, so longer text gets truncated anyway — be explicit about it.
- **Embedding cost:** every re-ingestion re-embeds. Add a `text_hash` column, skip embedding if hash matches existing. Small change, big savings.

---

## What "done" looks like

You can run:

```bash
node 08_search_cli.js
query› Brauche embedded firmware security review für zwei Wochen
```

…and get a result set where:

1. The top 3 results are sensible people/companies/Inserate (not random matches).
2. Each result's "Warum" cites **specific document IDs** that actually exist.
3. The footnotes resolve to real URLs you can click.
4. FHNW-scoped entities consistently float above CH-scoped ones when evidence is comparable.
5. An Inserat from Marc shows up as evidence on Marc's person card.

When that works on 4 real profiles + 2 fake companies + 3 Inserate, the prototype has earned the right to become a Spring Boot service.

---

## Why we're not jumping to Spring Boot

Three risks v8 forces us to confront cheaply:

1. **Source quality.** GitHub READMEs are often garbage. Personal websites range from beautifully structured to single-page-JS hell. We need to know what fraction of real people we can actually ingest before designing a UI around it.
2. **Citation faithfulness.** Will gemma4 (or whichever local model) actually cite the doc IDs it's given, or will it invent plausible-looking ones? If invention happens, we need a different model, a different prompt, or post-hoc citation extraction. Better to find out in a 300-line script than in a Spring AI bean.
3. **Re-rank weight calibration.** Scope as a 1.3×/1.15×/1.0× multiplier is a guess. v8 lets us tune it against real queries before it's baked into a service.

Spring Boot adds maven, modulith boundaries, JPA entities, REST controllers, security config, compose-driven Ollama — all of which are *plumbing for a working pipeline*. Don't plumb for a pipeline that doesn't work yet.
