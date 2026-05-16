import { Client } from "pg";

const OLLAMA_URL = "http://localhost:11343/api/embeddings";
const MODEL = "jeffh/intfloat-multilingual-e5-large:f16";

interface CompanyRow {
  name: string;
  zweck: string;
}

// A few sample Swiss-company-like rows
const sampleData: CompanyRow[] = [
  {
    name: "AlpTech AG",
    zweck:
      "Entwicklung von Software für die Bauindustrie, insbesondere BIM-Lösungen und digitale Bauplanung.",
  },
  {
    name: "BioGen Solutions GmbH",
    zweck:
      "Forschung und Entwicklung im Bereich der medizinischen Diagnostik und personalisierten Medizin.",
  },
  {
    name: "GreenEnergy Schweiz AG",
    zweck:
      "Planung und Realisierung von Photovoltaikanlagen sowie Beratung im Bereich erneuerbare Energien.",
  },
  {
    name: "DataMatch Analytics",
    zweck:
      "Beratungsdienstleistungen im Bereich Machine Learning und semantische Suche für KMU.",
  },
  {
    name: "RoboBaden GmbH",
    zweck:
      "Entwicklung von Industrierobotern und Automatisierungslösungen für die Fertigungsindustrie.",
  },
];

async function embed(text: string): Promise<number[]> {
  const res = await fetch(OLLAMA_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ model: MODEL, prompt: text }),
  });

  if (!res.ok) {
    throw new Error(`Ollama error ${res.status}: ${await res.text()}`);
  }

  const data = (await res.json()) as { embedding: number[] };
  return data.embedding;
}

// pgvector accepts vectors as strings like "[0.1,0.2,...]"
function toVectorLiteral(vec: number[]): string {
  return `[${vec.join(",")}]`;
}

async function main() {
  const client = new Client({
    host: "localhost",
    port: 5432,
    user: "root",
    password: "123456789",
    database: "badenconnect",
  });
  await client.connect();

  // Clean slate for the demo
  await client.query("TRUNCATE companies RESTART IDENTITY");

  console.log("Embedding and inserting sample data...");
  for (const row of sampleData) {
    // IMPORTANT: indexed documents get the "passage:" prefix
    const embedding = await embed(`passage: ${row.zweck}`);
    await client.query(
      "INSERT INTO companies (name, zweck, embedding) VALUES ($1, $2, $3)",
      [row.name, row.zweck, toVectorLiteral(embedding)],
    );
    console.log(`  ✓ ${row.name}`);
  }

  // Run a couple of free-text queries
  const queries = [
    "Ich suche eine Firma die mir bei KI und Datenanalyse helfen kann",
    "Unternehmen im Bereich nachhaltige Energie",
    "Automatisierung in der Produktion",
  ];

  for (const q of queries) {
    console.log(`\n🔍 Query: "${q}"`);
    // IMPORTANT: search inputs get the "query:" prefix
    const qEmbedding = await embed(`query: ${q}`);
    const { rows } = await client.query(
      `SELECT name, zweck, embedding <=> $1 AS distance
         FROM companies
         ORDER BY distance ASC
         LIMIT 3`,
      [toVectorLiteral(qEmbedding)],
    );
    for (const r of rows) {
      console.log(
        `   ${r.distance.toFixed(4)}  ${r.name} — ${r.zweck.slice(0, 70)}...`,
      );
    }
  }

  await client.end();
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
