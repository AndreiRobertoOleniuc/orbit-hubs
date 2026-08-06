// 03_search.js — Test semantic search against persons table
// Usage: node 03_search.js "Ich suche jemanden mit KI und Machine Learning Erfahrung"
import pkg from "pg";
const { Client } = pkg;

const client = new Client({
    host: "localhost",
    port: 5432,
    database: "badenconnect",
    user: "root",
    password: process.env.DB_PASSWORD,
});

const query = process.argv[2] ?? "Ich suche einen Fullstack Engineer mit Java Erfahrung";

// e5 models need "query: " prefix for search queries (different from "passage: " used at insert)
async function getQueryEmbedding(text) {
    const res = await fetch("http://localhost:11434/api/embeddings", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
            model: "jeffh/intfloat-multilingual-e5-large:f16",
            prompt: `query: ${text}`,
        }),
    });

    if (!res.ok) {
        throw new Error(`Ollama embedding failed: ${res.status} ${res.statusText}`);
    }

    const data = await res.json();
    return data.embedding;
}

async function main() {
    await client.connect();

    console.log(`\nSearch query: "${query}"\n`);
    console.log("Embedding query...");

    const vector = await getQueryEmbedding(query);

    const result = await client.query(
        `SELECT
       name,
       github,
       website,
       LEFT(description, 120) AS description_preview,
       1 - (embedding <=> $1::vector) AS score
     FROM persons
     ORDER BY embedding <=> $1::vector
     LIMIT 4`,
        [JSON.stringify(vector)]
    );

    console.log("\n--- Results ---\n");
    result.rows.forEach((row, i) => {
        console.log(`#${i + 1} ${row.name} (score: ${row.score.toFixed(4)})`);
        console.log(`  ${row.description_preview}...`);
        if (row.github) console.log(`  GitHub: ${row.github}`);
        if (row.website) console.log(`  Website: ${row.website}`);
        console.log();
    });

    await client.end();
}

main().catch(console.error);