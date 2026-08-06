// 05_hybrid_search.js — Hybrid retrieval: pgvector (dense) + Postgres FTS
// (lexical, BM25-ish) fused with Reciprocal Rank Fusion. Same query battery as
// 04 so we can compare directly.
//
// Postgres' built-in full-text search uses ts_rank_cd (cover density), not
// true BM25. For RRF that doesn't matter — only the ranking order is used —
// but if you want real BM25 swap in the pg_search / paradedb extension and
// replace the FTS leg below.

import pkg from "pg";
const { Client } = pkg;

const EMBEDDING_MODEL = "jeffh/intfloat-multilingual-e5-large:f16";
const KEEP_ALIVE = "10m";
const CANDIDATES = 50;   // per-leg pool size
const TOP_K = 5;          // final results shown
const RRF_K = 60;         // standard RRF constant

const client = new Client({
    host: "localhost",
    port: 5432,
    database: "badenconnect",
    user: "root",
    password: process.env.DB_PASSWORD,
});

const QUERIES = [
    { topic: "Embedded / IoT firmware",            q: "Wir entwickeln ein IoT-Gerät auf STM32 mit Sensor-Fusion und brauchen jemanden mit Embedded-Firmware-Erfahrung." },
    { topic: "Applied AI / LLM / RAG",             q: "Wir bauen ein RAG-System mit Large Language Models für die Industrie und suchen Expertise in MLOps und LLM-Anwendungen." },
    { topic: "Java / Spring Fullstack",            q: "Wir suchen einen erfahrenen Fullstack Engineer mit Java, Spring Boot, React und Cloud-Erfahrung (GCP oder AWS)." },
    { topic: "Network security / Telco",           q: "Für ein Projekt zu Netzwerksicherheit, Anomalieerkennung und Automatisierung suchen wir jemanden aus dem Telco-Umfeld." },
    { topic: "Systemtechnik / DB / Infrastruktur", q: "Wir brauchen einen Systemingenieur mit Erfahrung in SQL Server, Docker und Systemarchitektur für ein Industrieprojekt." },
    { topic: "iOS / Mobile App",                   q: "Wir entwickeln eine native iOS-App in Swift und suchen Mobile-Entwickler:innen mit Health/Fitness-Erfahrung." },
    { topic: "Data Engineering / Pipelines",       q: "Wir bauen eine Data Pipeline mit Python, Airflow und dbt und suchen Data Engineers mit Cloud-Erfahrung." },
    { topic: "Frontend / Design Systems",          q: "Wir brauchen Frontend-Entwickler:innen mit Tiefe in React, TypeScript und Design Systems." },
    { topic: "DevOps / Kubernetes",                q: "Für unsere Plattform suchen wir DevOps-Engineers mit Kubernetes, Terraform und CI/CD-Erfahrung." },
    { topic: "RegTech / FinTech",                  q: "Wir suchen Software-Engineers mit Erfahrung in RegTech, Compliance-Automatisierung und FinTech." },
    { topic: "Computer Vision / Edge AI",          q: "Wir entwickeln Computer-Vision-Pipelines auf Edge-Devices mit TensorFlow Lite und suchen passende Expertise." },
    { topic: "Research bridge FHNW",               q: "Wir suchen jemanden mit Brücke zwischen akademischer Forschung und industrieller Anwendung, idealerweise mit FHNW-Bezug." },
];

async function ensureFtsIndex() {
    // Use 'simple' dictionary: no stemming, no stopword removal. Our corpus is
    // heavy on tech proper nouns (Airflow, dbt, Kubernetes, RegTech) that
    // would be mangled by the german stemmer. We handle stopwords ourselves
    // when building the query.
    await client.query(`ALTER TABLE persons DROP COLUMN IF EXISTS description_tsv`);
    await client.query(`
        ALTER TABLE persons
        ADD COLUMN description_tsv tsvector
        GENERATED ALWAYS AS (to_tsvector('simple', coalesce(description,''))) STORED
    `);
    await client.query(`
        CREATE INDEX IF NOT EXISTS persons_description_tsv_idx
        ON persons USING GIN (description_tsv)
    `);
}

async function getQueryEmbedding(text) {
    const res = await fetch("http://localhost:11434/api/embeddings", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
            model: EMBEDDING_MODEL,
            prompt: `query: ${text}`,
            keep_alive: KEEP_ALIVE,
        }),
    });
    if (!res.ok) throw new Error(`Ollama embedding failed: ${res.status} ${res.statusText}`);
    const data = await res.json();
    return data.embedding;
}

async function denseSearch(qText) {
    const vec = await getQueryEmbedding(qText);
    const { rows } = await client.query(
        `SELECT id, name, description,
                1 - (embedding <=> $1::vector) AS score
         FROM persons
         ORDER BY embedding <=> $1::vector
         LIMIT ${CANDIDATES}`,
        [JSON.stringify(vec)]
    );
    return rows;
}

// Very small German + English stopword list — enough to keep tsquery focused
// on signal terms ("Airflow", "RegTech", "Kubernetes") instead of glue words.
const STOPWORDS = new Set([
    "wir","ich","du","sie","er","es","ein","eine","einen","einem","einer","eines",
    "und","oder","aber","mit","für","fur","ohne","auf","bei","im","in","der","die",
    "das","den","dem","des","zu","zur","zum","von","vom","aus","an","am","als","ist",
    "sind","war","waren","sein","haben","hat","hatte","wird","werden","wurde","wurden",
    "kann","können","konnte","brauchen","brauche","suchen","suche","jemanden","jemand",
    "passende","passenden","passendes","erfahrung","erfahrungen","experten","expertise",
    "für","fuer","das","dass","sich","aus","über","ueber","unsere","unser","unseren",
    "the","and","or","with","for","a","an","of","to","in","on","we","need","looking",
    "someone","experience","expert","expertise",
]);

function buildTsQuery(qText) {
    // Pull out alphabetic / alphanumeric tokens, drop stopwords + 2-char fragments.
    const tokens = qText
        .toLowerCase()
        .match(/[a-zà-ÿ0-9][a-zà-ÿ0-9\-\+\.]{1,}/gi) ?? [];
    const keep = [...new Set(tokens)]
        .filter((t) => t.length >= 3 && !STOPWORDS.has(t))
        .map((t) => t.replace(/[^a-z0-9à-ÿ\-]/gi, ""))
        .filter(Boolean);
    if (keep.length === 0) return null;
    // Quote each token to bypass the stemmer for proper nouns / tech terms
    // (e.g. "airflow", "dbt" shouldn't be reduced), then OR them.
    return keep.map((t) => `${t}:*`).join(" | ");
}

async function lexicalSearch(qText) {
    const tsq = buildTsQuery(qText);
    if (!tsq) return [];
    const { rows } = await client.query(
        `SELECT id, name, description,
                ts_rank_cd(description_tsv, to_tsquery('simple', $1)) AS score
         FROM persons
         WHERE description_tsv @@ to_tsquery('simple', $1)
         ORDER BY score DESC
         LIMIT ${CANDIDATES}`,
        [tsq]
    );
    return rows;
}

function rrfFuse(lists) {
    const acc = new Map(); // id -> { row, score, contribs }
    for (const list of lists) {
        list.rows.forEach((row, idx) => {
            const rank = idx + 1;
            const inc = 1 / (RRF_K + rank);
            const prev = acc.get(row.id) ?? { row, score: 0, contribs: {} };
            prev.score += inc;
            prev.contribs[list.label] = rank;
            acc.set(row.id, prev);
        });
    }
    return [...acc.values()].sort((a, b) => b.score - a.score);
}

async function runQuery({ topic, q }) {
    const [dense, lexical] = await Promise.all([
        denseSearch(q),
        lexicalSearch(q),
    ]);

    const fused = rrfFuse([
        { label: "vec", rows: dense },
        { label: "fts", rows: lexical },
    ]);

    console.log(`\n══════════════════════════════════════════════════════════════`);
    console.log(`▶ ${topic}`);
    console.log(`  "${q}"`);
    console.log(`──────────────────────────────────────────────────────────────`);
    console.log(`  dense pool: ${dense.length}   lexical pool: ${lexical.length}`);
    console.log(`──────────────────────────────────────────────────────────────`);

    fused.slice(0, TOP_K).forEach((item, i) => {
        const v = item.contribs.vec ?? "—";
        const f = item.contribs.fts ?? "—";
        console.log(`  #${i + 1}  rrf=${item.score.toFixed(4)}  vec=${String(v).padStart(2)}  fts=${String(f).padStart(2)}  ${item.row.name}`);
        console.log(`        ${item.row.description.slice(0, 160).replace(/\s+/g, " ")}…`);
    });

    // Side-by-side: what the dense leg alone would have returned, for the
    // queries where lexical kicks in (e.g. "Airflow", "dbt") this is where the
    // change shows up.
    console.log(`  ── dense-only top 3 (for comparison) ──`);
    dense.slice(0, 3).forEach((r, i) => {
        console.log(`     #${i + 1}  ${r.score.toFixed(4)}  ${r.name}`);
    });
}

async function main() {
    await client.connect();
    await ensureFtsIndex();

    const { rows: count } = await client.query(`SELECT COUNT(*)::int AS n FROM persons`);
    console.log(`persons table: ${count[0].n} rows · hybrid = pgvector + tsvector, fused with RRF (k=${RRF_K})`);

    for (const q of QUERIES) {
        await runQuery(q);
    }

    await client.end();
}

main().catch(async (e) => {
    console.error(e);
    try { await client.end(); } catch {}
    process.exit(1);
});
