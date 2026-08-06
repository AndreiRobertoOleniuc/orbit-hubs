// 04_eval_search.js — Run a battery of problem-statement queries against the
// persons table and print top matches so we can judge embedding quality.
import pkg from "pg";
const { Client } = pkg;

const EMBEDDING_MODEL = "jeffh/intfloat-multilingual-e5-large:f16";
const TOP_K = 5;
const KEEP_ALIVE = "10m";

const client = new Client({
    host: "localhost",
    port: 5432,
    database: "badenconnect",
    user: "root",
    password: process.env.DB_PASSWORD,
});

// Realistic "I'm looking for someone who can ..." style queries covering the
// main topical buckets we expect in the synthetic dataset.
const QUERIES = [
    {
        topic: "Embedded / IoT firmware",
        q: "Wir entwickeln ein IoT-Gerät auf STM32 mit Sensor-Fusion und brauchen jemanden mit Embedded-Firmware-Erfahrung.",
    },
    {
        topic: "Applied AI / LLM / RAG",
        q: "Wir bauen ein RAG-System mit Large Language Models für die Industrie und suchen Expertise in MLOps und LLM-Anwendungen.",
    },
    {
        topic: "Java / Spring Fullstack",
        q: "Wir suchen einen erfahrenen Fullstack Engineer mit Java, Spring Boot, React und Cloud-Erfahrung (GCP oder AWS).",
    },
    {
        topic: "Network security / Netzwerkautomatisierung",
        q: "Für ein Projekt zu Netzwerksicherheit, Anomalieerkennung und Automatisierung suchen wir jemanden aus dem Telco-Umfeld.",
    },
    {
        topic: "Systemtechnik / DB / Infrastruktur",
        q: "Wir brauchen einen Systemingenieur mit Erfahrung in SQL Server, Docker und Systemarchitektur für ein Industrieprojekt.",
    },
    {
        topic: "iOS / Mobile App",
        q: "Wir entwickeln eine native iOS-App in Swift und suchen Mobile-Entwickler:innen mit Health/Fitness-Erfahrung.",
    },
    {
        topic: "Data Engineering / Pipelines",
        q: "Wir bauen eine Data Pipeline mit Python, Airflow und dbt und suchen Data Engineers mit Cloud-Erfahrung.",
    },
    {
        topic: "Frontend / Design Systems",
        q: "Wir brauchen Frontend-Entwickler:innen mit Tiefe in React, TypeScript und Design Systems.",
    },
    {
        topic: "DevOps / Kubernetes",
        q: "Für unsere Plattform suchen wir DevOps-Engineers mit Kubernetes, Terraform und CI/CD-Erfahrung.",
    },
    {
        topic: "RegTech / FinTech",
        q: "Wir suchen Software-Engineers mit Erfahrung in RegTech, Compliance-Automatisierung und FinTech.",
    },
    {
        topic: "Computer Vision / Edge AI",
        q: "Wir entwickeln Computer-Vision-Pipelines auf Edge-Devices mit TensorFlow Lite und suchen passende Expertise.",
    },
    {
        topic: "Research collaboration FHNW",
        q: "Wir suchen jemanden mit Brücke zwischen akademischer Forschung und industrieller Anwendung, idealerweise mit FHNW-Bezug.",
    },
];

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

async function searchOne({ topic, q }) {
    const vec = await getQueryEmbedding(q);
    const { rows } = await client.query(
        `SELECT name,
                LEFT(description, 160) AS preview,
                1 - (embedding <=> $1::vector) AS score
         FROM persons
         ORDER BY embedding <=> $1::vector
         LIMIT ${TOP_K}`,
        [JSON.stringify(vec)]
    );

    console.log(`\n══════════════════════════════════════════════════════════════`);
    console.log(`▶ ${topic}`);
    console.log(`  "${q}"`);
    console.log(`──────────────────────────────────────────────────────────────`);
    rows.forEach((r, i) => {
        console.log(
            `  #${i + 1}  ${r.score.toFixed(4)}  ${r.name}`
        );
        console.log(`        ${r.preview.replace(/\s+/g, " ")}…`);
    });
}

async function main() {
    await client.connect();
    const { rows: count } = await client.query(`SELECT COUNT(*)::int AS n FROM persons`);
    console.log(`persons table: ${count[0].n} rows`);

    for (const q of QUERIES) {
        await searchOne(q);
    }

    await client.end();
}

main().catch(async (e) => {
    console.error(e);
    try { await client.end(); } catch {}
    process.exit(1);
});
