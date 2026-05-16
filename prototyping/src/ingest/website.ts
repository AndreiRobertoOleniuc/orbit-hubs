import { JSDOM } from "jsdom";
import { Readability } from "@mozilla/readability";

import { pool } from "../lib/db.js";
import { upsertChunks, ChunkInput } from "../lib/chunks.js";
import { chunkText } from "../lib/chunk_text.js";

export type IngestStatus = "ok" | "empty" | "failed";

export interface IngestResult {
  personId: string | null;
  status: IngestStatus;
  chunksCreated: number;
  reason?: string;
}

const FETCH_TIMEOUT_MS = 10_000;
const MIN_CONTENT_CHARS = 200;
const USER_AGENT = "BadenConnectBot/0.1 (thesis prototype)";

function canonicalUrl(raw: string): string {
  try {
    const u = new URL(raw);
    u.hash = "";
    // Strip a trailing slash on the path for stable identity (but keep "/").
    if (u.pathname.length > 1 && u.pathname.endsWith("/")) {
      u.pathname = u.pathname.replace(/\/+$/, "");
    }
    return u.toString();
  } catch {
    return raw;
  }
}

async function fetchHtml(url: string): Promise<string> {
  const ctl = new AbortController();
  const timer = setTimeout(() => ctl.abort(), FETCH_TIMEOUT_MS);
  try {
    const res = await fetch(url, {
      headers: { "User-Agent": USER_AGENT, Accept: "text/html,*/*" },
      redirect: "follow",
      signal: ctl.signal,
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const ct = res.headers.get("content-type") ?? "";
    if (!/html|text/i.test(ct) && ct !== "") {
      throw new Error(`non-html content-type: ${ct}`);
    }
    return await res.text();
  } finally {
    clearTimeout(timer);
  }
}

function extractReadable(html: string, url: string): string {
  const dom = new JSDOM(html, { url });
  const reader = new Readability(dom.window.document);
  const article = reader.parse();
  if (!article) return "";
  // textContent gives a clean stripped version; fall back to title+excerpt+text.
  const parts = [article.title, article.byline, article.textContent].filter(
    (s) => typeof s === "string" && s.trim().length > 0,
  ) as string[];
  return parts.join("\n\n").trim();
}

async function resolvePerson(
  url: string,
  displayName: string,
): Promise<string> {
  const client = await pool.connect();
  try {
    const existing = await client.query<{ person_id: string }>(
      `SELECT person_id FROM person_identities
        WHERE source = 'website' AND external_id = $1`,
      [url],
    );
    if (existing.rows.length > 0) return existing.rows[0]!.person_id;

    await client.query("BEGIN");
    const ins = await client.query<{ id: string }>(
      `INSERT INTO persons (display_name) VALUES ($1) RETURNING id`,
      [displayName],
    );
    const personId = ins.rows[0]!.id;
    await client.query(
      `INSERT INTO person_identities (person_id, source, external_id)
        VALUES ($1, 'website', $2)
        ON CONFLICT (source, external_id) DO NOTHING`,
      [personId, url],
    );
    await client.query("COMMIT");
    return personId;
  } catch (err) {
    await client.query("ROLLBACK").catch(() => {});
    throw err;
  } finally {
    client.release();
  }
}

export async function ingestWebsite(
  rawUrl: string,
  displayName: string,
  styleCategory: string,
): Promise<IngestResult> {
  const url = canonicalUrl(rawUrl);
  let html: string;
  try {
    html = await fetchHtml(url);
  } catch (err: any) {
    return {
      personId: null,
      status: "failed",
      chunksCreated: 0,
      reason: `fetch: ${err?.message ?? err}`,
    };
  }

  let text: string;
  try {
    text = extractReadable(html, url);
  } catch (err: any) {
    return {
      personId: null,
      status: "failed",
      chunksCreated: 0,
      reason: `parse: ${err?.message ?? err}`,
    };
  }

  if (!text || text.length < MIN_CONTENT_CHARS) {
    return {
      personId: null,
      status: "empty",
      chunksCreated: 0,
      reason: `content too short (${text.length} chars)`,
    };
  }

  const personId = await resolvePerson(url, displayName);

  const pieces = chunkText(text);
  if (pieces.length === 0) {
    return {
      personId,
      status: "empty",
      chunksCreated: 0,
      reason: "chunker produced no pieces",
    };
  }

  const total = pieces.length;
  const chunks: ChunkInput[] = pieces.map((p, i) => ({
    kind: "website_section",
    label: `${displayName} — section ${i + 1}/${total}`,
    content: p.content,
    sourceUrl: url,
    metadata: {
      style_category: styleCategory,
      position: p.position,
      total_chunks: total,
    },
  }));

  const created = await upsertChunks("person", personId, "website", chunks);
  return { personId, status: "ok", chunksCreated: created };
}
