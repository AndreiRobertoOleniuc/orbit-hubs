# industryProfiles — BadenConnect discovery prototype

Scratch area for proving the BadenConnect ingestion → evidence-cited discovery
pipeline on a lean Postgres-only stack, before the Spring Boot + React build.

The **current, proven implementation is v10** (`10_*.js`). Everything under
`archive/` is an earlier iteration kept for reference only.

## Layout

```
10_schema.sql      # all v10_ tables, generated tsv columns, HNSW indexes (re-runnable)
10_utils.js        # embeddings (Ollama), Gemini Flash rationale, semantic chunker, geocode, db pool, config
10_ingest.js       # sources → chunk → embed → upsert documents/inserate/entities/tags
10_search_cli.js   # 3-stage discovery: retrieve (RRF) → re-rank (rollup + scope) → lazy cited rationale

seeds/             # input data: people.json, companies.json, inserate/*.json
users/             # uploaded CV PDFs (the linkedin_cv source), one folder per person

docs/              # BadenConnect_v10_Spec.md (the spec) + 08_plan.md (predecessor plan)
archive/           # superseded prototypes v00–v08
archive/data/      # old synthetic-profile dumps + a chat transcript
```

## Prerequisites

- **Postgres + pgvector** on `localhost:5432` (db/user/pass in `.env`).
- **Ollama** on `localhost:11434` with the embedding model pulled
  (`jeffh/intfloat-multilingual-e5-large:f16`).
- **`GEMINI_API_KEY`** in the environment — used only for the lazy rationale;
  search and ingest work without it (rationale falls back gracefully).

Config (DB creds, model names, `GITHUB_PAT`) lives in `.env` (gitignored).

## Run (from this folder)

```bash
npm run schema         # create/refresh the v10_ tables
npm run ingest:reset   # wipe + ingest seeds (GitHub, websites, CV PDFs, inline docs)
npm run ingest         # idempotent re-ingest (skips re-embedding unchanged text)
npm run search "embedded firmware security review zwei wochen"
```

In the search REPL: a **number** generates the cited rationale for that result,
`more` pages, `new` starts a fresh query, `q` quits.

See `docs/BadenConnect_v10_Spec.md` §9 for the definition of done.
