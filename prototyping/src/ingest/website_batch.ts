import { readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";
import cliProgress from "cli-progress";

import { pool } from "../lib/db.js";
import { ingestWebsite, IngestResult } from "./website.js";

const CSV_PATH = "data/personal_sites.csv";
const REPORT_PATH = "data/ingestion_report.json";
const DELAY_MS = 500;

interface Row {
  url: string;
  display_name: string;
  style_category: string;
  notes: string;
}

function parseCsv(text: string): Row[] {
  const lines = text.split(/\r?\n/).filter((l) => l.length > 0);
  const header = lines.shift()!.split(",");
  return lines.map((line) => {
    const cells = line.split(",");
    const row: any = {};
    header.forEach((h, i) => {
      row[h.trim()] = (cells[i] ?? "").trim();
    });
    return row as Row;
  });
}

function sleep(ms: number) {
  return new Promise((r) => setTimeout(r, ms));
}

async function main() {
  const csv = readFileSync(resolve(CSV_PATH), "utf-8");
  const rows = parseCsv(csv);
  console.log(`Loaded ${rows.length} sites from ${CSV_PATH}`);

  const bar = new cliProgress.SingleBar(
    {
      format:
        "  {bar} {percentage}% | {value}/{total} | ok {ok} empty {empty} failed {failed} | last: {last}",
      barCompleteChar: "█",
      barIncompleteChar: "░",
      hideCursor: true,
    },
    cliProgress.Presets.shades_classic,
  );

  const results: Array<{
    url: string;
    display_name: string;
    style_category: string;
    result: IngestResult;
  }> = [];

  let ok = 0;
  let empty = 0;
  let failed = 0;
  let totalChunks = 0;

  bar.start(rows.length, 0, { ok, empty, failed, last: "" });

  for (const row of rows) {
    let result: IngestResult;
    try {
      result = await ingestWebsite(
        row.url,
        row.display_name,
        row.style_category,
      );
    } catch (err: any) {
      result = {
        personId: null,
        status: "failed",
        chunksCreated: 0,
        reason: `unhandled: ${err?.message ?? err}`,
      };
    }

    if (result.status === "ok") {
      ok++;
      totalChunks += result.chunksCreated;
    } else if (result.status === "empty") empty++;
    else failed++;

    results.push({
      url: row.url,
      display_name: row.display_name,
      style_category: row.style_category,
      result,
    });

    bar.increment(1, {
      ok,
      empty,
      failed,
      last: `${row.display_name} (${result.status})`,
    });

    await sleep(DELAY_MS);
  }
  bar.stop();

  const summary = {
    timestamp: new Date().toISOString(),
    total: rows.length,
    ok,
    empty,
    failed,
    total_chunks_created: totalChunks,
    results,
  };
  writeFileSync(resolve(REPORT_PATH), JSON.stringify(summary, null, 2));

  console.log("");
  console.log(`Wrote report to ${REPORT_PATH}`);
  console.log(
    `Summary: ok=${ok}  empty=${empty}  failed=${failed}  total_chunks=${totalChunks}`,
  );

  const problems = results.filter((r) => r.result.status !== "ok");
  if (problems.length > 0) {
    console.log("\nNon-ok sites:");
    for (const p of problems) {
      console.log(
        `  [${p.result.status}] ${p.url}  — ${p.result.reason ?? ""}`,
      );
    }
  }

  await pool.end();
}

main().catch(async (err) => {
  console.error(err);
  await pool.end().catch(() => {});
  process.exit(1);
});
