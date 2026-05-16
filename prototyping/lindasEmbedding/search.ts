import { Client } from "pg";
import * as readline from "node:readline/promises";
import { stdin as input, stdout as output } from "node:process";

const OLLAMA_URL = "http://localhost:11434/api/embeddings";
const MODEL = "jeffh/intfloat-multilingual-e5-large:f16";
const TOP_K = 10;

async function embed(text: string): Promise<number[]> {
  const res = await fetch(OLLAMA_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ model: MODEL, prompt: text }),
  });
  if (!res.ok) throw new Error(`Ollama ${res.status}: ${await res.text()}`);
  const data = (await res.json()) as { embedding: number[] };
  return data.embedding;
}

function toVectorLiteral(vec: number[]): string {
  return `[${vec.join(",")}]`;
}

// ANSI colors for nicer output
const c = {
  dim: (s: string) => `\x1b[2m${s}\x1b[0m`,
  bold: (s: string) => `\x1b[1m${s}\x1b[0m`,
  green: (s: string) => `\x1b[32m${s}\x1b[0m`,
  yellow: (s: string) => `\x1b[33m${s}\x1b[0m`,
  red: (s: string) => `\x1b[31m${s}\x1b[0m`,
  cyan: (s: string) => `\x1b[36m${s}\x1b[0m`,
};

// Color-code distance: lower = more similar
function colorDist(d: number): string {
  const s = d.toFixed(4);
  if (d < 0.15) return c.green(s);
  if (d < 0.25) return c.yellow(s);
  return c.red(s);
}

function truncate(s: string, n: number): string {
  if (!s) return "";
  return s.length > n ? s.slice(0, n - 1) + "…" : s;
}

async function search(client: Client, query: string) {
  const t0 = Date.now();

  // IMPORTANT: search inputs get "query:" prefix for multilingual-e5
  const vec = await embed(`query: ${query}`);
  const embedMs = Date.now() - t0;

  const t1 = Date.now();
  const { rows } = await client.query<{
    legal_name: string;
    zweck: string;
    locality: string;
    canton: string;
    distance: number;
  }>(
    `SELECT legal_name, zweck, locality, canton, embedding <=> $1 AS distance
       FROM companies
       WHERE embedding IS NOT NULL
       ORDER BY distance ASC
       LIMIT ${TOP_K}`,
    [toVectorLiteral(vec)],
  );
  const queryMs = Date.now() - t1;

  console.log(
    c.dim(
      `\n  ${rows.length} results | embed ${embedMs}ms | search ${queryMs}ms\n`,
    ),
  );

  rows.forEach((r, i) => {
    const rank = c.bold(`${(i + 1).toString().padStart(2)}.`);
    const dist = colorDist(r.distance);
    const name = c.cyan(r.legal_name);
    const loc = r.locality ? c.dim(`  ${r.locality}`) : "";
    console.log(`${rank}  ${dist}  ${name}${loc}`);
    console.log(`      ${c.dim(truncate(r.zweck, 200))}\n`);
  });
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

  const { rows } = await client.query<{ n: string }>(
    "SELECT count(*)::text AS n FROM companies WHERE embedding IS NOT NULL",
  );
  console.log(c.bold(`\n  BadenConnect semantic search`));
  if (rows.length > 0)
    console.log(c.dim(`  ${rows[0]!.n} embedded companies in canton Aargau`));

  console.log(c.dim(`  type a query (or "exit" to quit)\n`));

  const rl = readline.createInterface({ input, output });

  while (true) {
    const q = (await rl.question(c.bold("› "))).trim();
    if (!q) continue;
    if (q === "exit" || q === "quit" || q === ":q") break;

    try {
      await search(client, q);
    } catch (err: any) {
      console.error(c.red(`  ! ${err.message}\n`));
    }
  }

  rl.close();
  await client.end();
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
