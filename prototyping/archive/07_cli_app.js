// 07_cli_app.js — BadenConnect-style CLI. Hybrid retrieval + per-match LLM
// rationale ("why this person") + extracted role/skill chips + full profile.
//
// Usage:  node 07_cli_app.js
//   query› Security Review JWT Authentication, 2-3 Stunden, nächste Woche
//
// :q to exit.

import readline from "node:readline";
import pkg from "pg";
const { Client } = pkg;

const EMBEDDING_MODEL = "jeffh/intfloat-multilingual-e5-large:f16";
const RATIONALE_MODEL = "batiai/gemma4-e2b:q4";
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
    if (!res.ok) throw new Error(`embedding failed: ${res.status}`);
    return (await res.json()).embedding;
}

async function denseSearch(qText) {
    const vec = await getQueryEmbedding(qText);
    const { rows } = await client.query(
        `SELECT id, name, github, website, description
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
        `SELECT id, name, github, website, description
         FROM persons
         WHERE description_tsv @@ to_tsquery('simple', $1)
         ORDER BY ts_rank_cd(description_tsv, to_tsquery('simple', $1)) DESC
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
            const prev = acc.get(row.id) ?? { row, score: 0 };
            prev.score += 1 / (RRF_K + rank);
            acc.set(row.id, prev);
        });
    }
    return [...acc.values()].sort((a, b) => b.score - a.score);
}

// ── LLM rationale ───────────────────────────────────────────────────────────
const RATIONALE_PROMPT = (query, profile) => `Du analysierst, wie gut eine Person zu einer Suchanfrage auf einer Schweizer Innovationsplattform passt.

Suchanfrage:
"${query}"

Profil:
${profile}

Gib eine JSON-Antwort zurück mit genau diesem Schema (keine Erklärung davor oder danach, nur JSON):
{
  "rolle": "kurze Rolle, max 6 Wörter (z.B. 'Senior Security Engineer')",
  "kontext": "Firma, Studium oder Selbstständig — max 6 Wörter",
  "skills": ["max 3 konkrete Skill-Tags die zur Anfrage passen, je max 4 Wörter"],
  "warum": "2 Sätze, konkret: warum diese Person zu dieser spezifischen Anfrage passt. Nenne konkrete Belege aus dem Profil. Deutsch.",
  "vorbehalte": "1 Satz wenn etwas fehlt oder unsicher ist, sonst null. Deutsch."
}`;

async function callLLM(prompt) {
    const res = await fetch("http://localhost:11434/api/generate", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
            model: RATIONALE_MODEL,
            prompt,
            stream: false,
            keep_alive: KEEP_ALIVE,
            format: "json",
        }),
    });
    if (!res.ok) throw new Error(`LLM failed: ${res.status}`);
    return (await res.json()).response;
}

async function explain(query, person) {
    try {
        const raw = await callLLM(RATIONALE_PROMPT(query, person.description));
        return JSON.parse(raw);
    } catch (e) {
        return {
            rolle: "—",
            kontext: "—",
            skills: [],
            warum: `(Rationale-Generierung fehlgeschlagen: ${e.message})`,
            vorbehalte: null,
        };
    }
}

// ── Rendering ───────────────────────────────────────────────────────────────
const c = {
    dim:    (s) => `\x1b[2m${s}\x1b[0m`,
    bold:   (s) => `\x1b[1m${s}\x1b[0m`,
    cyan:   (s) => `\x1b[36m${s}\x1b[0m`,
    blue:   (s) => `\x1b[34m${s}\x1b[0m`,
    yellow: (s) => `\x1b[33m${s}\x1b[0m`,
    green:  (s) => `\x1b[32m${s}\x1b[0m`,
    inv:    (s) => `\x1b[7m${s}\x1b[0m`,
};

function header(query) {
    const bar = "─".repeat(78);
    console.log("");
    console.log(c.bold(c.blue("  ▣ BadenConnect")) + c.dim("    Experten finden · Inserate · Meine Anfragen · Nachrichten"));
    console.log(c.dim(`  ${bar}`));
    console.log(`  ${c.dim("🔍")}  ${c.bold(query)}`);
    console.log(c.dim(`  ${bar}`));
}

function chip(s) {
    return c.blue(`[ ${s} ]`);
}

function wrap(text, width = 76, indent = "     ") {
    const words = text.split(/\s+/);
    const lines = [];
    let cur = "";
    for (const w of words) {
        if ((cur + " " + w).trim().length > width) {
            lines.push(cur.trim());
            cur = w;
        } else {
            cur += " " + w;
        }
    }
    if (cur.trim()) lines.push(cur.trim());
    return lines.map((l) => indent + l).join("\n");
}

function renderResult(rank, person, rationale) {
    const initials = person.name
        .split(/\s+/).filter(Boolean).slice(0, 2)
        .map((w) => w[0]).join("").toUpperCase();

    const bar = "─".repeat(78);
    console.log("");
    console.log(`  ${c.dim(String(rank).padStart(2, "0"))}  ${c.inv(` ${initials} `)}  ${c.bold(person.name)}`);
    console.log(`       ${c.dim(`${rationale.rolle} · ${rationale.kontext}`)}`);

    if (rationale.skills?.length) {
        console.log(`       ${rationale.skills.map(chip).join("  ")}`);
    }

    console.log("");
    console.log(`     ${c.bold(c.yellow("Warum diese Person?"))}`);
    console.log(wrap(rationale.warum, 72));

    if (rationale.vorbehalte) {
        console.log("");
        console.log(`     ${c.dim("⚠ Vorbehalt:")}`);
        console.log(wrap(rationale.vorbehalte, 72));
    }

    console.log("");
    console.log(`     ${c.dim("Vollständiges Profil")}`);
    console.log(wrap(person.description, 72));

    const links = [];
    if (person.github)  links.push(`GitHub: ${person.github}`);
    if (person.website) links.push(`Web:    ${person.website.slice(0, 60)}…`);
    if (links.length) {
        console.log("");
        for (const l of links) console.log(`     ${c.dim(l)}`);
    }

    console.log(c.dim(`  ${bar}`));
}

// ── Main flow ──────────────────────────────────────────────────────────────
async function runQuery(q) {
    header(q);

    const t0 = Date.now();
    const [dense, lexical] = await Promise.all([denseSearch(q), lexicalSearch(q)]);
    const fused = rrfFuse([
        { label: "vec", rows: dense },
        { label: "fts", rows: lexical },
    ]);
    const retrievalMs = Date.now() - t0;

    if (fused.length === 0) {
        console.log(c.yellow("  Keine passenden Personen gefunden."));
        return;
    }

    const top = fused.slice(0, TOP_K);
    console.log(c.dim(`  ${top.length} Personen gefunden · Retrieval ${retrievalMs}ms · generiere Begründungen…`));

    for (let i = 0; i < top.length; i++) {
        const item = top[i];
        process.stdout.write(c.dim(`  · ${item.row.name}…`));
        const tll = Date.now();
        const rationale = await explain(q, item.row);
        process.stdout.write(c.dim(` ${Date.now() - tll}ms\r`));
        process.stdout.write(" ".repeat(78) + "\r");
        renderResult(i + 1, item.row, rationale);
    }
}

async function repl() {
    const rl = readline.createInterface({
        input: process.stdin,
        output: process.stdout,
        prompt: c.bold(c.cyan("\nquery› ")),
    });

    console.log("");
    console.log(c.dim("  BadenConnect CLI · type a query, :q to exit"));
    rl.prompt();

    let busy = false;
    let pendingClose = false;

    rl.on("line", async (line) => {
        const q = line.trim();
        if (!q) return rl.prompt();
        if (q === ":q" || q === ":quit" || q === "exit") { rl.close(); return; }
        busy = true;
        try {
            await runQuery(q);
        } catch (e) {
            console.error(c.yellow(`error: ${e.message}`));
        }
        busy = false;
        if (pendingClose) { rl.close(); return; }
        rl.prompt();
    });

    rl.on("close", async () => {
        if (busy) { pendingClose = true; return; }
        await client.end();
        process.exit(0);
    });
}

async function main() {
    await client.connect();
    await ensureFtsIndex();
    const { rows } = await client.query(`SELECT COUNT(*)::int AS n FROM persons`);
    console.log(c.dim(`Connected · ${rows[0].n} Personen im Index · retrieval=${EMBEDDING_MODEL.split("/").pop()} · rationale=${RATIONALE_MODEL}`));
    await repl();
}

main().catch(async (e) => {
    console.error(e);
    try { await client.end(); } catch {}
    process.exit(1);
});
