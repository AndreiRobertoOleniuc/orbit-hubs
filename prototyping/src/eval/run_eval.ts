import { readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";

import { pool } from "../lib/db.js";
import { searchEntities, EntityResult } from "../search/search.js";

const QUERIES_PATH = "data/eval_queries.json";
const RESULTS_PATH = "data/eval_results.json";
const MODEL = "jeffh/intfloat-multilingual-e5-large:f16";

interface EvalQuery {
  id: string;
  query: string;
  expected_urls: string[];
  difficulty: string;
}

async function urlsForPerson(personId: string): Promise<string[]> {
  const { rows } = await pool.query<{ external_id: string }>(
    `SELECT external_id FROM person_identities
      WHERE person_id = $1::uuid AND source = 'website'`,
    [personId],
  );
  return rows.map((r) => r.external_id);
}

function canonical(u: string): string {
  try {
    const x = new URL(u);
    x.hash = "";
    if (x.pathname.length > 1 && x.pathname.endsWith("/")) {
      x.pathname = x.pathname.replace(/\/+$/, "");
    }
    return x.toString();
  } catch {
    return u;
  }
}

async function entityUrls(e: EntityResult): Promise<string[]> {
  if (e.entity_type !== "person") return [];
  return (await urlsForPerson(e.entity_id)).map(canonical);
}

async function evalQuery(q: EvalQuery) {
  const expected = new Set(q.expected_urls.map(canonical));
  const top15 = await searchEntities(q.query, undefined, 15);

  const enriched = await Promise.all(
    top15.map(async (e, i) => ({
      rank: i + 1,
      entity_type: e.entity_type,
      entity_id: e.entity_id,
      display_name: e.display_name,
      best_distance: e.best_distance,
      via_label: e.best_chunk.chunk_label,
      via_url: e.best_chunk.source_url,
      urls: await entityUrls(e),
    })),
  );

  const hitsAtK = (k: number) =>
    enriched.slice(0, k).filter((r) => r.urls.some((u) => expected.has(u)))
      .length;

  const retrieved10 = new Set<string>();
  for (const r of enriched.slice(0, 10)) {
    for (const u of r.urls) if (expected.has(u)) retrieved10.add(u);
  }

  return {
    id: q.id,
    query: q.query,
    difficulty: q.difficulty,
    expected_urls: [...expected],
    p_at_5: hitsAtK(5) / 5,
    p_at_10: hitsAtK(10) / 10,
    recall_at_10: expected.size === 0 ? 0 : retrieved10.size / expected.size,
    top_results: enriched.slice(0, 10),
  };
}

function mean(xs: number[]): number {
  return xs.length === 0 ? 0 : xs.reduce((a, b) => a + b, 0) / xs.length;
}

function fmt(n: number) {
  return n.toFixed(3);
}

async function main() {
  const queries: EvalQuery[] = JSON.parse(
    readFileSync(resolve(QUERIES_PATH), "utf-8"),
  );
  console.log(`Running ${queries.length} eval queries…\n`);

  const perQuery: any[] = [];
  for (const q of queries) {
    const t0 = Date.now();
    const r = await evalQuery(q);
    const ms = Date.now() - t0;
    perQuery.push({ ...r, latency_ms: ms });
    console.log(
      `[${r.id}] ${r.difficulty.padEnd(22)}  P@5=${fmt(r.p_at_5)}  P@10=${fmt(r.p_at_10)}  R@10=${fmt(r.recall_at_10)}  (${ms}ms)`,
    );
    console.log(`        "${r.query}"`);
    const expectedSet = new Set(r.expected_urls);
    for (const row of r.top_results.slice(0, 5)) {
      const hit = row.urls.some((u: string) => expectedSet.has(u));
      const mark = hit ? "✓" : " ";
      console.log(
        `        ${mark} #${String(row.rank).padStart(2)} ${row.best_distance.toFixed(4)}  ${row.display_name}  (via ${row.via_label})`,
      );
    }
    console.log("");
  }

  const macro = {
    p_at_5: mean(perQuery.map((r) => r.p_at_5)),
    p_at_10: mean(perQuery.map((r) => r.p_at_10)),
    recall_at_10: mean(perQuery.map((r) => r.recall_at_10)),
    avg_latency_ms: mean(perQuery.map((r) => r.latency_ms)),
  };

  console.log("─".repeat(60));
  console.log(
    `Macro: P@5=${fmt(macro.p_at_5)}  P@10=${fmt(macro.p_at_10)}  R@10=${fmt(macro.recall_at_10)}  avg=${macro.avg_latency_ms.toFixed(0)}ms`,
  );

  const out = {
    timestamp: new Date().toISOString(),
    model: MODEL,
    num_queries: queries.length,
    macro,
    per_query: perQuery,
  };
  writeFileSync(resolve(RESULTS_PATH), JSON.stringify(out, null, 2));
  console.log(`\nWrote ${RESULTS_PATH}`);

  await pool.end();
}

main().catch(async (err) => {
  console.error(err);
  await pool.end().catch(() => {});
  process.exit(1);
});
