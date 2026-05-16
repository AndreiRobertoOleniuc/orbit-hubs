import { createReadStream, readFileSync } from "node:fs";
import { createInterface } from "node:readline";
import { Client } from "pg";
import cliProgress from "cli-progress";

const CSV_PATH = "zefix_aargau.csv";
const OLLAMA_URL = "http://localhost:11434/api/embeddings";
const MODEL = "jeffh/intfloat-multilingual-e5-large:f16";

const CONCURRENCY = 12; // parallel Ollama calls
const DB_BATCH_SIZE = 200; // rows per multi-row INSERT
const MAX_INPUT_CHARS = 1500;

interface CsvRow {
  company_uri: string;
  uid: string;
  legal_name: string;
  description: string; // -> zweck
  street: string;
  postal_code: string;
  locality: string;
  canton: string;
  muni_id: string;
  muni_name: string;
}

// --- minimal but correct CSV parser (handles quoted fields with embedded commas/quotes/newlines) ---
function parseCsvLine(line: string): string[] {
  const out: string[] = [];
  let cur = "";
  let inQuotes = false;
  for (let i = 0; i < line.length; i++) {
    const c = line[i];
    if (inQuotes) {
      if (c === '"' && line[i + 1] === '"') {
        cur += '"';
        i++;
      } else if (c === '"') inQuotes = false;
      else cur += c;
    } else {
      if (c === '"') inQuotes = true;
      else if (c === ",") {
        out.push(cur);
        cur = "";
      } else cur += c;
    }
  }
  out.push(cur);
  return out;
}

async function* readCsv(path: string): AsyncGenerator<CsvRow> {
  const rl = createInterface({
    input: createReadStream(path),
    crlfDelay: Infinity,
  });
  let header: string[] | null = null;
  for await (const line of rl) {
    if (!line) continue;
    const cells = parseCsvLine(line);
    if (!header) {
      header = cells;
      continue;
    }
    const row: any = {};
    header.forEach((h, i) => {
      row[h] = cells[i] ?? "";
    });
    yield row as CsvRow;
  }
}

// Count CSV rows once at startup (excluding header). Fast for ~50k lines.
function countCsvRows(path: string): number {
  const content = readFileSync(path, "utf-8");
  return content.split("\n").filter((l) => l.length > 0).length - 1;
}

async function embed(text: string): Promise<number[]> {
  const truncated =
    text.length > MAX_INPUT_CHARS ? text.slice(0, MAX_INPUT_CHARS) : text;

  const res = await fetch(OLLAMA_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ model: MODEL, prompt: truncated }),
  });
  if (!res.ok) throw new Error(`Ollama ${res.status}: ${await res.text()}`);
  const data = (await res.json()) as { embedding: number[] };
  return data.embedding;
}

function toVectorLiteral(vec: number[]): string {
  return `[${vec.join(",")}]`;
}

async function flushBatch(
  client: Client,
  batch: Array<CsvRow & { embedding: number[] }>,
) {
  if (batch.length === 0) return;
  const cols = [
    "uid",
    "company_uri",
    "legal_name",
    "zweck",
    "street",
    "postal_code",
    "locality",
    "canton",
    "muni_id",
    "muni_name",
    "embedding",
  ];
  const placeholders: string[] = [];
  const values: any[] = [];
  batch.forEach((row, i) => {
    const base = i * cols.length;
    placeholders.push(`(${cols.map((_, j) => `$${base + j + 1}`).join(",")})`);
    values.push(
      row.uid,
      row.company_uri,
      row.legal_name,
      row.description,
      row.street || null,
      row.postal_code || null,
      row.locality || null,
      row.canton || null,
      row.muni_id || null,
      row.muni_name || null,
      toVectorLiteral(row.embedding),
    );
  });

  const sql = `
    INSERT INTO companies (${cols.join(",")})
    VALUES ${placeholders.join(",")}
    ON CONFLICT (uid) DO UPDATE SET
      legal_name = EXCLUDED.legal_name,
      zweck = EXCLUDED.zweck,
      embedding = EXCLUDED.embedding,
      embedded_at = now()
  `;
  await client.query(sql, values);
}

async function* mapConcurrent<T, R>(
  source: AsyncIterable<T>,
  fn: (item: T) => Promise<R>,
  concurrency: number,
): AsyncGenerator<R> {
  const buffer: T[] = [];
  for await (const item of source) {
    buffer.push(item);
    if (buffer.length >= concurrency) {
      const results = await Promise.all(buffer.map(fn));
      for (const r of results) yield r;
      buffer.length = 0;
    }
  }
  if (buffer.length > 0) {
    const results = await Promise.all(buffer.map(fn));
    for (const r of results) yield r;
  }
}

async function main() {
  const totalRows = countCsvRows(CSV_PATH);
  console.log(`CSV: ${totalRows} rows`);

  const client = new Client({
    host: "localhost",
    port: 5432,
    user: "root",
    password: "123456789",
    database: "badenconnect",
  });
  await client.connect();

  const existing = await client.query<{ uid: string }>(
    "SELECT uid FROM companies",
  );
  const done = new Set(existing.rows.map((r) => r.uid));
  if (done.size > 0) {
    console.log(`↻ Resuming: ${done.size} rows already embedded`);
  }

  const bar = new cliProgress.SingleBar(
    {
      format:
        "  {bar} {percentage}% | {value}/{total} | {rate} emb/s | ETA {eta_formatted} | skipped {skipped}",
      barCompleteChar: "█",
      barIncompleteChar: "░",
      hideCursor: true,
      etaBuffer: 100, // smooth ETA over last 100 updates
    },
    cliProgress.Presets.shades_classic,
  );

  // Start at resume offset so % and ETA reflect overall progress
  bar.start(totalRows, done.size, { rate: "0.0", skipped: 0 });

  const t0 = Date.now();
  let processed = 0;
  let skipped = 0;
  let batch: Array<CsvRow & { embedding: number[] }> = [];

  const tick = () => {
    const rate = processed / ((Date.now() - t0) / 1000 || 1);
    bar.update(done.size + processed, {
      rate: rate.toFixed(1),
      skipped,
    });
  };

  async function* toEmbed() {
    for await (const row of readCsv(CSV_PATH)) {
      if (!row.description?.trim()) {
        skipped++;
        tick();
        continue;
      }
      if (done.has(row.uid)) {
        skipped++;
        tick();
        continue;
      }
      yield row;
    }
  }

  const embedded = mapConcurrent(
    toEmbed(),
    async (row) => {
      const vec = await embed(`passage: ${row.description}`);
      return { ...row, embedding: vec };
    },
    CONCURRENCY,
  );

  for await (const row of embedded) {
    batch.push(row);
    processed++;

    if (batch.length >= DB_BATCH_SIZE) {
      await flushBatch(client, batch);
      batch = [];
    }
    tick();
  }

  await flushBatch(client, batch);
  bar.stop();

  const totalSec = ((Date.now() - t0) / 1000).toFixed(1);
  console.log(
    `\n✓ Done: ${processed} embedded, ${skipped} skipped, ${totalSec}s total`,
  );

  await client.end();
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
