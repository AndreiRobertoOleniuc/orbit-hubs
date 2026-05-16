import * as readline from "node:readline/promises";
import { stdin as input, stdout as output } from "node:process";

import { pool, toVectorLiteral } from "../lib/db.js";
import { embedText } from "../lib/embed.js";

const CANDIDATE_K = 50;
const TOP_ENTITIES = 15;

const c = {
  dim: (s: string) => `\x1b[2m${s}\x1b[0m`,
  bold: (s: string) => `\x1b[1m${s}\x1b[0m`,
  green: (s: string) => `\x1b[32m${s}\x1b[0m`,
  yellow: (s: string) => `\x1b[33m${s}\x1b[0m`,
  red: (s: string) => `\x1b[31m${s}\x1b[0m`,
  cyan: (s: string) => `\x1b[36m${s}\x1b[0m`,
  magenta: (s: string) => `\x1b[35m${s}\x1b[0m`,
};

function colorDist(d: number): string {
  const s = d.toFixed(4);
  if (d < 0.3) return c.green(s);
  if (d < 0.5) return c.yellow(s);
  return c.red(s);
}

function truncate(s: string, n: number): string {
  if (!s) return "";
  const t = s.replace(/\s+/g, " ").trim();
  return t.length > n ? t.slice(0, n - 1) + "…" : t;
}

export interface ChunkHit {
  chunk_id: number;
  entity_type: string;
  entity_id: string;
  source: string;
  chunk_kind: string;
  chunk_label: string;
  content: string;
  source_url: string | null;
  distance: number;
}

export interface EntityResult {
  entity_type: string;
  entity_id: string;
  best_distance: number;
  best_chunk: ChunkHit;
  display_name: string;
  hit_count: number;
}

export async function retrieveChunks(
  query: string,
  filterType?: string,
): Promise<ChunkHit[]> {
  const vec = await embedText(query, "query");
  const lit = toVectorLiteral(vec);

  const params: any[] = [lit];
  let where = "embedding IS NOT NULL";
  if (filterType) {
    params.push(filterType);
    where += ` AND entity_type = $${params.length}`;
  }

  const sql = `
    SELECT id AS chunk_id, entity_type, entity_id, source, chunk_kind,
           chunk_label, content, source_url,
           embedding <=> $1 AS distance
      FROM chunks
     WHERE ${where}
     ORDER BY distance ASC
     LIMIT ${CANDIDATE_K}
  `;
  const { rows } = await pool.query<ChunkHit>(sql, params);
  return rows;
}

export function aggregateMaxPool(hits: ChunkHit[]): EntityResult[] {
  const byEntity = new Map<string, EntityResult>();
  for (const h of hits) {
    const key = `${h.entity_type}:${h.entity_id}`;
    const cur = byEntity.get(key);
    if (!cur) {
      byEntity.set(key, {
        entity_type: h.entity_type,
        entity_id: h.entity_id,
        best_distance: h.distance,
        best_chunk: h,
        display_name: "",
        hit_count: 1,
      });
    } else {
      cur.hit_count++;
      if (h.distance < cur.best_distance) {
        cur.best_distance = h.distance;
        cur.best_chunk = h;
      }
    }
  }
  return [...byEntity.values()].sort(
    (a, b) => a.best_distance - b.best_distance,
  );
}

export async function attachDisplayNames(
  entities: EntityResult[],
): Promise<void> {
  const byType: Record<string, string[]> = {};
  for (const e of entities) {
    (byType[e.entity_type] ??= []).push(e.entity_id);
  }

  const nameMap = new Map<string, string>();

  if (byType.company?.length) {
    const ids = byType.company.map((s) => Number(s)).filter(Number.isFinite);
    if (ids.length) {
      const { rows } = await pool.query<{ id: number; legal_name: string }>(
        `SELECT id, legal_name FROM companies WHERE id = ANY($1::int[])`,
        [ids],
      );
      for (const r of rows) nameMap.set(`company:${r.id}`, r.legal_name);
    }
  }
  if (byType.person?.length) {
    const { rows } = await pool.query<{ id: string; display_name: string }>(
      `SELECT id::text AS id, display_name FROM persons WHERE id = ANY($1::uuid[])`,
      [byType.person],
    );
    for (const r of rows) nameMap.set(`person:${r.id}`, r.display_name);
  }
  if (byType.publication?.length) {
    const { rows } = await pool.query<{ id: string; title: string }>(
      `SELECT id::text AS id, title FROM publications WHERE id = ANY($1::uuid[])`,
      [byType.publication],
    );
    for (const r of rows) nameMap.set(`publication:${r.id}`, r.title);
  }

  for (const e of entities) {
    e.display_name =
      nameMap.get(`${e.entity_type}:${e.entity_id}`) ??
      `<${e.entity_type} ${e.entity_id}>`;
  }
}

export async function searchEntities(
  query: string,
  filterType?: string,
  topN: number = TOP_ENTITIES,
): Promise<EntityResult[]> {
  const hits = await retrieveChunks(query, filterType);
  const entities = aggregateMaxPool(hits).slice(0, topN);
  await attachDisplayNames(entities);
  return entities;
}

function parseArgs(argv: string[]): { filterType?: string } {
  let filterType: string | undefined;
  for (const a of argv) {
    const m = a.match(/^--type=(.+)$/);
    if (m) filterType = m[1];
  }
  return { filterType };
}

async function main() {
  const { filterType } = parseArgs(process.argv.slice(2));

  const { rows } = await pool.query<{ n: string }>(
    `SELECT count(*)::text AS n FROM chunks WHERE embedding IS NOT NULL`,
  );
  console.log(c.bold("\n  BadenConnect unified semantic search"));
  console.log(c.dim(`  ${rows[0]?.n ?? "?"} embedded chunks indexed`));
  if (filterType) console.log(c.dim(`  filter: entity_type=${filterType}`));
  console.log(c.dim(`  type a query (or "exit" to quit)\n`));

  const rl = readline.createInterface({ input, output });
  while (true) {
    const q = (await rl.question(c.bold("› "))).trim();
    if (!q) continue;
    if (q === "exit" || q === "quit" || q === ":q") break;

    const t0 = Date.now();
    try {
      const results = await searchEntities(q, filterType);
      const ms = Date.now() - t0;
      console.log(c.dim(`\n  ${results.length} entities | ${ms}ms\n`));
      results.forEach((r, i) => {
        const rank = c.bold(`${(i + 1).toString().padStart(2)}.`);
        const dist = colorDist(r.best_distance);
        const name = c.cyan(r.display_name);
        const type = c.magenta(`[${r.entity_type}]`);
        console.log(
          `${rank}  ${dist}  ${type} ${name}  ${c.dim(`(${r.hit_count} hit${r.hit_count === 1 ? "" : "s"})`)}`,
        );
        console.log(
          `      ${c.dim("via")} ${r.best_chunk.chunk_label}  ${c.dim(`[${r.best_chunk.chunk_kind}]`)}`,
        );
        if (r.best_chunk.source_url) {
          console.log(`      ${c.dim(r.best_chunk.source_url)}`);
        }
        console.log(`      ${c.dim(truncate(r.best_chunk.content, 150))}\n`);
      });
    } catch (err: any) {
      console.error(c.red(`  ! ${err.message}\n`));
    }
  }
  rl.close();
  await pool.end();
}

if (import.meta.url === `file://${process.argv[1]}`) {
  main().catch(async (err) => {
    console.error(err);
    await pool.end().catch(() => {});
    process.exit(1);
  });
}
