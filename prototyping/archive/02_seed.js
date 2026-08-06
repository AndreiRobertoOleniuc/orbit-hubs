// 02_seed.js — Embeds each person via Ollama and inserts into persons table
import fs from "node:fs";
import path from "node:path";
import pkg from "pg";
const { Client } = pkg;

const DOCS_FILE = path.resolve("profile_documents.jsonl");
const RAW_FILE = path.resolve("raw_profiles_300.json");

const EMBEDDING_MODEL = "jeffh/intfloat-multilingual-e5-large:f16";
// Keep the embedding model resident so we don't reload it between rows.
// Accepts a duration string ("30m", "1h") or seconds. Use -1 to keep forever.
const KEEP_ALIVE = "10m";

const client = new Client({
    host: "localhost",
    port: 5432,
    database: "badenconnect",
    user: "root",
    password: process.env.DB_PASSWORD,
});

function loadPeople() {
    const docs = fs
        .readFileSync(DOCS_FILE, "utf8")
        .split("\n")
        .filter(Boolean)
        .map((l) => JSON.parse(l));

    const rawByName = new Map();
    if (fs.existsSync(RAW_FILE)) {
        const raw = JSON.parse(fs.readFileSync(RAW_FILE, "utf8"));
        for (const p of raw) rawByName.set(p.name, p);
    }

    return docs.map((d) => {
        const raw = rawByName.get(d.name) || {};
        const githubUrls = Array.isArray(raw.github)
            ? raw.github.map((r) => r.name).join(", ")
            : null;
        return {
            name: d.name,
            github: githubUrls,
            website: raw.website || null,
            description: d.document,
        };
    });
}

// e5 models need "passage: " prefix for documents being stored
async function getEmbedding(text) {
    const res = await fetch("http://localhost:11434/api/embeddings", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
            model: EMBEDDING_MODEL,
            prompt: `passage: ${text}`,
            keep_alive: KEEP_ALIVE,
        }),
    });

    if (!res.ok) {
        throw new Error(`Ollama embedding failed: ${res.status} ${res.statusText}`);
    }

    const data = await res.json();
    return data.embedding; // Ollama returns { embedding: float[] }
}

async function main() {
    const people = loadPeople();
    console.log(`Loaded ${people.length} profile documents`);

    await client.connect();
    console.log("Connected to PostgreSQL\n");

    let i = 0;
    for (const person of people) {
        i++;
        const start = Date.now();
        console.log(`[${i}/${people.length}] Embedding: ${person.name}...`);

        const vector = await getEmbedding(person.description);

        await client.query(
            `INSERT INTO persons (name, github, website, description, embedding)
             VALUES ($1, $2, $3, $4, $5::vector)`,
            [
                person.name,
                person.github,
                person.website,
                person.description,
                JSON.stringify(vector),
            ]
        );

        const dt = ((Date.now() - start) / 1000).toFixed(2);
        console.log(`  ✓ Inserted (${vector.length} dims, ${dt}s)`);
    }

    console.log(`\nAll ${people.length} people seeded. Run 03_search.js to test matching.`);
    await client.end();
}

main().catch(async (e) => {
    console.error(e);
    try { await client.end(); } catch {}
    process.exit(1);
});
