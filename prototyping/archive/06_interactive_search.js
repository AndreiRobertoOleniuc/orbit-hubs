// 06_interactive_search.js — Interactive hybrid search REPL.
//
// Usage:
//   node 06_interactive_search.js                # REPL mode
//   node 06_interactive_search.js "your query"   # one-shot
//
// Same retrieval as 05 (pgvector + tsvector fused with RRF) plus ts_headline
// for the lexical-leg highlight so you can see which words triggered the
// match.

import readline from "node:readline";
import pkg from "pg";
const { Client } = pkg;

const EMBEDDING_MODEL = "jeffh/intfloat-multilingual-e5-large:f16";
const KEEP_ALIVE = "30m";
const CANDIDATES = 50;
const TOP_K = 5;
const RRF_K = 60;

const client = new Client({
    host: "localhost",
    port: 5432,
    database: "badenconnect",
    user: "root",
    password: process.env.DB_PASSWORD,
});

const STOPWORDS = new Set([
    "wir","ich","du","sie","er","es","ein","eine","einen","einem","einer","eines",
    "und","oder","aber","mit","für","fur","ohne","auf","bei","im","in","der","die",
    "das","den","dem","des","zu","zur","zum","von","vom","aus","an","am","als","ist",
    "sind","war","waren","sein","haben","hat","hatte","wird","werden","wurde","wurden",
    "kann","können","konnte","brauchen","brauche","suchen","suche","jemanden","jemand",
    "passende","passenden","passendes","erfahrung","erfahrungen","experten","expertise",
    "fuer","dass","sich","über","ueber","unsere","unser","unseren",
    "the","and","or","with","for","an","of","to","on","we","need","looking",
    "someone","experience","expert",
]);

function buildTsQuery(qText) {
    const tokens = qText.toLowerCase().match(/[a-zà-ÿ0-9][a-zà-ÿ0-9\-\+\.]{1,}/gi) ?? [];
    const keep = [...new Set(tokens)]
        .filter((t) => t.length >= 3 && !STOPWORDS.has(t))
        .map((t) => t.replace(/[^a-z0-9à-ÿ\-]/gi, ""))
        .filter(Boolean);
    if (keep.length === 0) return null;
    return keep.map((t) => `${t}:*`).join(" | ");
}

async function ensureFtsIndex() {
    const { rows } = await client.query(`
        SELECT 1 FROM information_schema.columns
        WHERE table_name='persons' AND column_name='description_tsv'
    `);
    if (rows.length === 0) {
        await client.query(`
            ALTER TABLE persons ADD COLUMN description_tsv tsvector
            GENERATED ALWAYS AS (to_tsvector('simple', coalesce(description,''))) STORED
        `);
        await client.query(`
            CREATE INDEX IF NOT EXISTS persons_description_tsv_idx
            ON persons USING GIN (description_tsv)
        `);
    }
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
    return (await res.json()).embedding;
}

async function denseSearch(qText) {
    const vec = await getQueryEmbedding(qText);
    const { rows } = await client.query(
        `SELECT id, name, github, website, description,
                1 - (embedding <=> $1::vector) AS score
         FROM persons
         ORDER BY embedding <=> $1::vector
         LIMIT ${CANDIDATES}`,
        [JSON.stringify(vec)]
    );
    return rows;
}

async function lexicalSearch(qText) {
    const tsq = buildTsQuery(qText);
    if (!tsq) return [];
    const { rows } = await client.query(
        `SELECT id, name, github, website, description,
                ts_rank_cd(description_tsv, to_tsquery('simple', $1)) AS score,
                ts_headline('simple', description, to_tsquery('simple', $1),
                    'StartSel=«, StopSel=», MaxFragments=2, MaxWords=14, MinWords=6'
                ) AS snippet
         FROM persons
         WHERE description_tsv @@ to_tsquery('simple', $1)
         ORDER BY score DESC
         LIMIT ${CANDIDATES}`,
        [tsq]
    );
    return rows;
}

function rrfFuse(lists) {
    const acc = new Map();
    for (const list of lists) {
        list.rows.forEach((row, idx) => {
            const rank = idx + 1;
            const prev = acc.get(row.id) ?? { row, score: 0, contribs: {}, snippet: null };
            prev.score += 1 / (RRF_K + rank);
            prev.contribs[list.label] = rank;
            if (row.snippet) prev.snippet = row.snippet;
            // Prefer the row that has the snippet field
            if (row.snippet && !prev.row.snippet) prev.row = row;
            acc.set(row.id, prev);
        });
    }
    return [...acc.values()].sort((a, b) => b.score - a.score);
}

// ANSI helpers — keep it readable in the terminal.
const c = {
    dim: (s) => `\x1b[2m${s}\x1b[0m`,
    bold: (s) => `\x1b[1m${s}\x1b[0m`,
    cyan: (s) => `\x1b[36m${s}\x1b[0m`,
    green: (s) => `\x1b[32m${s}\x1b[0m`,
    yellow: (s) => `\x1b[33m${s}\x1b[0m`,
};

function paintSnippet(snippet) {
    if (!snippet) return null;
    return snippet
        .replace(/«/g, "\x1b[33m\x1b[1m")
        .replace(/»/g, "\x1b[0m")
        .replace(/\s+/g, " ")
        .trim();
}

async function runQuery(q) {
    const tsq = buildTsQuery(q);
    const t0 = Date.now();
    const [dense, lexical] = await Promise.all([denseSearch(q), lexicalSearch(q)]);
    const fused = rrfFuse([
        { label: "vec", rows: dense },
        { label: "fts", rows: lexical },
    ]);
    const ms = Date.now() - t0;

    console.log("");
    console.log(c.bold(c.cyan(`▶ "${q}"`)));
    console.log(c.dim(`  dense=${dense.length}  lexical=${lexical.length}  tsquery=${tsq ?? "(none)"}  ${ms}ms`));
    console.log("");

    if (fused.length === 0) {
        console.log(c.yellow("  No matches."));
        return;
    }

    fused.slice(0, TOP_K).forEach((item, i) => {
        const v = item.contribs.vec ?? "—";
        const f = item.contribs.fts ?? "—";
        const tag =
            item.contribs.vec && item.contribs.fts ? c.green("both")
            : item.contribs.vec ? c.cyan("dense")
            : c.yellow("lexical");

        console.log(`${c.bold(`#${i + 1}`)}  ${c.bold(item.row.name)}   ${c.dim(`vec=${v} fts=${f}  [${tag}]`)}`);

        const snippet = paintSnippet(item.snippet);
        if (snippet) {
            console.log(`     ${c.dim("evidence:")} …${snippet}…`);
        } else {
            console.log(`     ${c.dim(item.row.description.slice(0, 160).replace(/\s+/g, " "))}…`);
        }

        const links = [];
        if (item.row.github) links.push(`gh: ${item.row.github}`);
        if (item.row.website) links.push(`web: ${item.row.website}`);
        if (links.length) console.log(`     ${c.dim(links.join("   "))}`);
        console.log("");
    });
}

async function repl() {
    const rl = readline.createInterface({
        input: process.stdin,
        output: process.stdout,
        prompt: c.bold(c.cyan("query› ")),
    });

    console.log(c.dim("Interactive hybrid search. Type a query, or :quit to exit."));
    rl.prompt();

    rl.on("line", async (line) => {
        const q = line.trim();
        if (!q) return rl.prompt();
        if (q === ":quit" || q === ":q" || q === "exit") {
            rl.close();
            return;
        }
        try {
            await runQuery(q);
        } catch (e) {
            console.error(c.yellow(`error: ${e.message}`));
        }
        rl.prompt();
    });

    rl.on("close", async () => {
        await client.end();
        process.exit(0);
    });
}

async function main() {
    await client.connect();
    await ensureFtsIndex();
    const { rows } = await client.query(`SELECT COUNT(*)::int AS n FROM persons`);
    console.log(c.dim(`persons table: ${rows[0].n} rows · hybrid retrieval (pgvector + tsvector, RRF k=${RRF_K})`));

    const argQuery = process.argv.slice(2).join(" ").trim();
    if (argQuery) {
        await runQuery(argQuery);
        await client.end();
        return;
    }
    await repl();
}

main().catch(async (e) => {
    console.error(e);
    try { await client.end(); } catch {}
    process.exit(1);
});
