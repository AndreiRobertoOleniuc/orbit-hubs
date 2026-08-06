# Discovery prototype (`industryProfiles`) — methodological evolution

This folder is the **lean Postgres-only prototype** in which the Orbit discovery
pipeline was worked out **before** the Spring Boot + React product was built. It is
included in the submission as **evidence of the iterative method** behind the search
engine: how the approach evolved from a single-strategy semantic search to the
combined *hybrid retrieval → cross-encoder rerank → grounded rationale* pipeline that
the production system uses.

It is a **scratch/experimentation area**, not production code. The current, proven
stage is **v10** (`10_*.js`); everything under `archive/` is an earlier numbered
iteration, kept to document the progression.

> **Submission note.** Real `.env` credentials, `node_modules/`, uploaded CV PDFs,
> synthetic-profile data dumps and seed files with real public identifiers were
> **removed** from this copy for privacy and size. Use `.env.example` and your own
> seed data to run it. The value here is the **code progression**, not the data.

## The progression at a glance (00 → 10)

| Stage | File(s) | What it added | Why it mattered |
|---|---|---|---|
| **00** | `archive/00_generate_raw_profiles.js`, `00_github.js` | Synthetic test profiles (LLM-generated) + first GitHub fetch | A dataset to evaluate retrieval quality against |
| **01** | `archive/01_setup.js`, `01_turnIntoDocument.js` | DB setup; raw data → per-profile documents | The unit of retrieval was the whole profile document |
| **02** | `archive/02_seed.js` | Embed + seed the `persons` table | First searchable corpus |
| **03** | `archive/03_search.js` | **Semantic-only search** (pgvector) | The single-strategy **baseline** |
| **04** | `archive/04_eval_search.js` | **Evaluation harness**: a fixed query battery over the baseline | Made retrieval quality *judgeable* instead of anecdotal |
| **05** | `archive/05_hybrid_search.js` | **Hybrid retrieval**: dense (pgvector) + lexical (Postgres FTS), fused with **Reciprocal Rank Fusion** — run on the **same query battery as 04**, printing *dense-only* results side-by-side | The key comparison: hybrid caught exact terms (e.g. `Airflow`, `dbt`) that semantic-only missed, while semantic caught cross-language meaning that lexical missed |
| **06–07** | `archive/06_interactive_search.js`, `07_cli_app.js` | Interactive hybrid REPL; CLI with per-match **LLM rationale** | Turned ranked hits into an evidence-cited, explainable result |
| **08** | `archive/08_*` | **Multi-source ingestion** (GitHub, website, LinkedIn/CV PDF, inserat adapters) + entity summarisation, behind one schema | Generalised from a flat profile table to adapter-based ingestion |
| **10** | `10_schema.sql`, `10_utils.js`, `10_ingest.js`, `10_search_cli.js` | The consolidated v10: 3-stage discovery **retrieve (RRF) → re-rank (rollup + scope) → lazy cited rationale** | The design that was then productionised in the Spring Boot backend |

The **03 → 04 → 05** step is the direct evidence for the thesis argument that *no
single retrieval strategy was sufficient* and that the combination measurably improved
match quality (Kap. 6.5 and Forschungsfrage B). The **08 → 10** step mirrors the
Entity–Source–Chunk model and the search pipeline of the production system (Kap. 6).

## Layout

```
10_schema.sql      # v10 tables, generated tsvector columns, HNSW indexes (re-runnable)
10_utils.js        # embeddings (Ollama), Gemini rationale, semantic chunker, db pool, config
10_ingest.js       # sources -> chunk -> embed -> upsert
10_search_cli.js   # 3-stage discovery: retrieve (RRF) -> re-rank (scope) -> lazy cited rationale
docs/              # BadenConnect_v10_Spec.md (spec) + 08_plan.md (predecessor plan)
archive/           # the numbered progression 00-08 (superseded, kept as evidence)
```

## Prerequisites

- **PostgreSQL + pgvector** on `localhost:5432` (credentials via `.env`).
- **Ollama** on `localhost:11434` with `jeffh/intfloat-multilingual-e5-large:f16` pulled.
- `GEMINI_API_KEY` optional — only the lazy rationale needs it; search/ingest degrade
  gracefully without it.

## Run (v10, from this folder)

```bash
cp .env.example .env          # then fill in DB_PASSWORD etc.
npm install
npm run schema                # create/refresh the v10_ tables (destructive; re-ingest after)
npm run ingest:reset          # wipe + ingest your own seed data
npm run search "embedded firmware security review zwei wochen"
```

To reproduce the **single-vs-hybrid comparison** that motivated the design, run the
archived baseline and hybrid harnesses against the same corpus:

```bash
node archive/04_eval_search.js     # semantic-only baseline over the query battery
node archive/05_hybrid_search.js   # hybrid (dense + FTS + RRF), prints dense-only side-by-side
```

See `docs/BadenConnect_v10_Spec.md` §9 for the v10 definition of done.
