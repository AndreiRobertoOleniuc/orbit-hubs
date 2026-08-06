// 10_ingest.js — sources -> semantic chunk -> embed -> upsert.
//
// Fills v10_entities, v10_documents, v10_inserate, v10_tags (+ link/connection
// tables). Re-running upserts on stable IDs (no duplicates) and skips
// re-embedding unchanged text via the text_hash check.
//
// Sources per entity:
//   - inline `documents` in the seed JSON  (deterministic; always available)
//   - GitHub bio + top repos              (live, best-effort, needs GITHUB_PAT)
//   - personal website                    (live, best-effort)
//   - CV PDF under users/<FirstName>/*.pdf (the linkedin_cv substitute)
//
// Run:   node 10_ingest.js            (upsert)
//        node 10_ingest.js --reset    (truncate v10 content first)

import fs from "node:fs"
import path from "node:path"
import { JSDOM } from "jsdom"
import { PDFParse } from "pdf-parse"
import {
  pool,
  embedPassage,
  semanticChunks,
  textHash,
  geocode,
  geminiText,
} from "./10_utils.js"

const GITHUB_PAT = process.env.GITHUB_PAT
const SUMMARIZE = process.env.INGEST_SUMMARY !== "0"
const ROOT = process.cwd()

// ── Tag helpers ──────────────────────────────────────────────────────────────
function tagLabel(slug) {
  return "#" + slug.split("-").map((w) => w.charAt(0).toUpperCase() + w.slice(1)).join("")
}

async function upsertTag(slug) {
  await pool.query(
    `INSERT INTO v10_tags (slug, label) VALUES ($1, $2) ON CONFLICT (slug) DO NOTHING`,
    [slug, tagLabel(slug)]
  )
}

async function linkEntityTags(entityId, tags = []) {
  for (const t of tags) {
    await upsertTag(t)
    await pool.query(
      `INSERT INTO v10_entity_tags (entity_id, tag_slug) VALUES ($1, $2) ON CONFLICT DO NOTHING`,
      [entityId, t]
    )
  }
}

async function linkInseratTags(inseratId, tags = []) {
  for (const t of tags) {
    await upsertTag(t)
    await pool.query(
      `INSERT INTO v10_inserat_tags (inserat_id, tag_slug) VALUES ($1, $2) ON CONFLICT DO NOTHING`,
      [inseratId, t]
    )
  }
}

// ── Document upsert (chunk -> hash-check -> embed -> store) ───────────────────
let embeddedCount = 0
let skippedCount = 0

// One "source document" may yield several chunk rows. Each row id is the base id
// (single chunk) or base-cN (multiple). text_hash on the chunk lets us skip
// re-embedding when nothing changed.
async function upsertSourceDocument({ baseId, entityId, sourceType, sourceUrl, sourceLabel, text, meta = {} }) {
  const chunks = await semanticChunks(text)
  if (chunks.length === 0) return
  for (let i = 0; i < chunks.length; i++) {
    const chunk = chunks[i]
    const id = chunks.length === 1 ? baseId : `${baseId}-c${i}`
    const label = chunks.length === 1 ? sourceLabel : `${sourceLabel} (${i + 1})`
    const hash = textHash(chunk)

    const existing = await pool.query(
      `SELECT text_hash, embedding IS NOT NULL AS has_emb FROM v10_documents WHERE id = $1`,
      [id]
    )
    const row = existing.rows[0]
    if (row && row.text_hash === hash && row.has_emb) {
      // Unchanged — touch fetched_at, skip the expensive embedding.
      await pool.query(`UPDATE v10_documents SET fetched_at = NOW() WHERE id = $1`, [id])
      skippedCount++
      continue
    }

    const embedding = await embedPassage(chunk)
    await pool.query(
      `INSERT INTO v10_documents (id, entity_id, source_type, source_url, source_label, text, embedding, text_hash, meta, fetched_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9, NOW())
       ON CONFLICT (id) DO UPDATE SET
         text = EXCLUDED.text, embedding = EXCLUDED.embedding, text_hash = EXCLUDED.text_hash,
         source_label = EXCLUDED.source_label, meta = EXCLUDED.meta, fetched_at = NOW()`,
      [id, entityId, sourceType, sourceUrl, label, chunk, JSON.stringify(embedding), hash, JSON.stringify({ ...meta, chunk: i })]
    )
    embeddedCount++
  }
}

// ── Entity upsert ─────────────────────────────────────────────────────────────
async function upsertEntity(e, type) {
  const { lat, lng } = geocode(e.location)
  await pool.query(
    `INSERT INTO v10_entities (id, type, name, headline, location, location_lat, location_lng, scope, handelsregister_uid, updated_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9, NOW())
     ON CONFLICT (id) DO UPDATE SET
       name = EXCLUDED.name, headline = EXCLUDED.headline, location = EXCLUDED.location,
       location_lat = EXCLUDED.location_lat, location_lng = EXCLUDED.location_lng,
       scope = EXCLUDED.scope, handelsregister_uid = EXCLUDED.handelsregister_uid, updated_at = NOW()`,
    [e.id, type, e.name, e.headline || null, e.location || null, lat, lng, e.scope || "ch", e.handelsregister_uid || null]
  )
}

async function recordSource(entityId, sourceType, sourceRef, status) {
  await pool.query(
    `INSERT INTO v10_source_connections (id, entity_id, source_type, source_ref, sync_status, last_synced_at)
     VALUES ($1,$2,$3,$4,$5, NOW())
     ON CONFLICT (id) DO UPDATE SET sync_status = EXCLUDED.sync_status, last_synced_at = NOW()`,
    [`${entityId}-${sourceType}`, entityId, sourceType, sourceRef, status]
  )
}

// ── Live source: GitHub ───────────────────────────────────────────────────────
async function gh(pathStr) {
  const res = await fetch(`https://api.github.com${pathStr}`, {
    headers: {
      ...(GITHUB_PAT ? { Authorization: `token ${GITHUB_PAT}` } : {}),
      Accept: "application/vnd.github.v3+json",
      "User-Agent": "BadenConnect-Prototype/0.1",
    },
  })
  if (res.status === 404) return null
  if (!res.ok) throw new Error(`GitHub ${res.status} for ${pathStr}`)
  return res.json()
}

async function ingestGitHub(entityId, username) {
  const user = await gh(`/users/${username}`)
  if (user) {
    const bio = `${user.name || username}. ${user.bio || ""}. Standort: ${user.location || "unbekannt"}. Follower: ${user.followers}.`
    await upsertSourceDocument({
      baseId: `gh-${username}-bio`, entityId, sourceType: "github_bio",
      sourceUrl: `https://github.com/${username}`, sourceLabel: "GitHub · Profil", text: bio,
      meta: { followers: user.followers, location: user.location },
    })
  }
  const repos = await gh(`/users/${username}/repos?sort=stars&direction=desc&per_page=8`)
  if (Array.isArray(repos)) {
    for (const repo of repos.filter((r) => !r.fork).slice(0, 5)) {
      let readme = ""
      try {
        const r = await gh(`/repos/${username}/${repo.name}/readme`)
        if (r?.content) readme = Buffer.from(r.content, "base64").toString("utf8")
      } catch { /* repo without README */ }
      const text = `${repo.name} — ${repo.description || "keine Beschreibung"}. Sprache: ${repo.language || "unbekannt"}. Themen: ${(repo.topics || []).join(", ")}.\n\n${readme}`
      await upsertSourceDocument({
        baseId: `gh-${username}-repo-${repo.name}`, entityId, sourceType: "github_repo",
        sourceUrl: repo.html_url, sourceLabel: `GitHub · ${repo.name}`, text,
        meta: { stars: repo.stargazers_count, language: repo.language, topics: repo.topics },
      })
    }
  }
}

// ── Live source: website ──────────────────────────────────────────────────────
async function ingestWebsite(entityId, url) {
  const res = await fetch(url, { headers: { "User-Agent": "BadenConnect-Prototype/0.1 (contact@example.com)" } })
  if (!res.ok) throw new Error(`website ${res.status}`)
  const html = await res.text()
  const dom = new JSDOM(html, { url })
  const title = dom.window.document.title || url
  const text = dom.window.document.body.textContent.replace(/\s+/g, " ").trim()
  const slug = url.replace(/[^a-z0-9]/gi, "-").toLowerCase().slice(0, 60)
  await upsertSourceDocument({
    baseId: `web-${slug}`, entityId, sourceType: "website_page",
    sourceUrl: url, sourceLabel: `Website · ${title}`, text, meta: { title },
  })
}

// ── Source: CV PDF (the linkedin_cv substitute) ──────────────────────────────
async function ingestCvPdf(entityId, pdfPath) {
  const buffer = fs.readFileSync(pdfPath)
  const parser = new PDFParse({ data: buffer })
  const result = await parser.getText()
  await parser.destroy()
  const file = path.basename(pdfPath)
  await upsertSourceDocument({
    baseId: `cv-${entityId}`, entityId, sourceType: "linkedin_cv",
    sourceUrl: `linkedin-cv://uploaded/${file}`, sourceLabel: "CV · Lebenslauf",
    text: result.text, meta: { file },
  })
}

// ── Entity card summary (LLM-synthesized; NOT embedded, never retrieved) ─────
async function summarizeEntity(entityId, name) {
  if (!SUMMARIZE) return
  const { rows } = await pool.query(
    `SELECT text FROM v10_documents WHERE entity_id = $1 ORDER BY char_length(text) DESC LIMIT 4`,
    [entityId]
  )
  if (rows.length === 0) return
  const ctx = rows.map((r) => r.text).join("\n---\n").slice(0, 4000)
  try {
    const summary = await geminiText(
      `Fasse anhand der folgenden Quellen in EINEM deutschen Satz zusammen, worauf sich ${name} fachlich spezialisiert. Keine Anrede, kein Markdown.\n\n${ctx}`
    )
    await pool.query(`UPDATE v10_entities SET summary = $2 WHERE id = $1`, [entityId, summary])
  } catch (err) {
    console.log(`    (summary skipped for ${entityId}: ${err.message})`)
  }
}

// ── Inserat ingest ────────────────────────────────────────────────────────────
async function ingestInserat(ins) {
  const { lat, lng } = geocode(ins.location)
  // Effective status: a past valid_until auto-deactivates, regardless of the seed value.
  const expired = ins.valid_until && new Date(ins.valid_until) < new Date()
  const status = ins.status === "closed" ? "closed" : expired ? "expired" : ins.status || "active"

  const text = `${ins.title}\n\n${ins.description}`
  const hash = textHash(text)
  const existing = await pool.query(`SELECT text_hash, embedding IS NOT NULL AS has_emb FROM v10_inserate WHERE id = $1`, [ins.id])
  const prev = existing.rows[0]
  let embedding
  if (prev && prev.text_hash === hash && prev.has_emb) {
    embedding = null // unchanged: keep the stored vector
    skippedCount++
  } else {
    embedding = await embedPassage(text)
    embeddedCount++
  }

  await pool.query(
    `INSERT INTO v10_inserate
       (id, kind, title, description, owner_entity_id, region, location, location_lat, location_lng,
        honorar_mode, honorar_text, valid_until, status, embedding, text_hash)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15)
     ON CONFLICT (id) DO UPDATE SET
       kind = EXCLUDED.kind, title = EXCLUDED.title, description = EXCLUDED.description,
       owner_entity_id = EXCLUDED.owner_entity_id, region = EXCLUDED.region, location = EXCLUDED.location,
       location_lat = EXCLUDED.location_lat, location_lng = EXCLUDED.location_lng,
       honorar_mode = EXCLUDED.honorar_mode, honorar_text = EXCLUDED.honorar_text,
       valid_until = EXCLUDED.valid_until, status = EXCLUDED.status,
       embedding = COALESCE(EXCLUDED.embedding, v10_inserate.embedding),
       text_hash = EXCLUDED.text_hash`,
    [ins.id, ins.kind, ins.title, ins.description, ins.owner_entity_id || null, ins.region || "ch",
     ins.location || null, lat, lng, ins.honorar_mode || null, ins.honorar_text || null,
     ins.valid_until || null, status, embedding ? JSON.stringify(embedding) : null, hash]
  )
  await linkInseratTags(ins.id, ins.tags)
}

// ── Orchestration ─────────────────────────────────────────────────────────────
function readJson(p) {
  return JSON.parse(fs.readFileSync(p, "utf8"))
}

async function ingestEntity(e, type) {
  console.log(`> ${type}: ${e.name}`)
  await upsertEntity(e, type)
  await linkEntityTags(e.id, e.tags)

  // Inline (deterministic) documents.
  for (const d of e.documents || []) {
    await recordSource(e.id, d.source_type, d.source_url, "synced")
    await upsertSourceDocument({
      baseId: d.id, entityId: e.id, sourceType: d.source_type,
      sourceUrl: d.source_url, sourceLabel: d.source_label, text: d.text, meta: d.meta || {},
    })
  }

  // Live GitHub.
  if (e.github) {
    try {
      await ingestGitHub(e.id, e.github)
      await recordSource(e.id, "github", e.github, "synced")
    } catch (err) {
      console.log(`    GitHub failed (${e.github}): ${err.message}`)
      await recordSource(e.id, "github", e.github, "error")
    }
  }

  // Live website.
  if (e.website) {
    try {
      await ingestWebsite(e.id, e.website)
      await recordSource(e.id, "website", e.website, "synced")
    } catch (err) {
      console.log(`    Website failed (${e.website}): ${err.message}`)
      await recordSource(e.id, "website", e.website, "error")
    }
  }

  // CV PDF under users/<FirstName>/.
  const firstName = e.name.split(" ")[0]
  const userDir = path.join(ROOT, "users", firstName)
  if (fs.existsSync(userDir)) {
    for (const f of fs.readdirSync(userDir).filter((f) => f.toLowerCase().endsWith(".pdf"))) {
      try {
        await ingestCvPdf(e.id, path.join(userDir, f))
        await recordSource(e.id, "linkedin_cv", f, "synced")
      } catch (err) {
        console.log(`    CV failed (${f}): ${err.message}`)
      }
    }
  }

  await summarizeEntity(e.id, e.name)
}

async function main() {
  const reset = process.argv.includes("--reset")
  if (reset) {
    console.log("!! --reset: clearing v10 content")
    await pool.query(
      `TRUNCATE v10_inserat_tags, v10_entity_tags, v10_tag_subscriptions,
               v10_source_connections, v10_inserate, v10_documents, v10_entities RESTART IDENTITY CASCADE`
    )
  }

  // Inserate first only matters for FK to owners; owners are people, ingested below,
  // but the FK is ON DELETE SET NULL and nullable, so order is flexible. People & companies first.
  const people = readJson(path.join(ROOT, "seeds", "people.json"))
  const companies = fs.existsSync(path.join(ROOT, "seeds", "companies.json"))
    ? readJson(path.join(ROOT, "seeds", "companies.json"))
    : []

  console.log("\n--- Entities ---")
  for (const c of companies) await ingestEntity(c, "company")
  for (const p of people) await ingestEntity(p, "person")

  console.log("\n--- Inserate ---")
  const insDir = path.join(ROOT, "seeds", "inserate")
  if (fs.existsSync(insDir)) {
    for (const f of fs.readdirSync(insDir).filter((f) => f.endsWith(".json"))) {
      const ins = readJson(path.join(insDir, f))
      console.log(`> inserat: ${ins.id} [${ins.kind}]`)
      await ingestInserat(ins)
    }
  }

  const counts = await pool.query(`
    SELECT
      (SELECT count(*) FROM v10_entities) AS entities,
      (SELECT count(*) FROM v10_documents) AS documents,
      (SELECT count(*) FROM v10_documents WHERE embedding IS NULL) AS docs_no_emb,
      (SELECT count(*) FROM v10_inserate) AS inserate,
      (SELECT count(*) FROM v10_inserate WHERE embedding IS NULL) AS ins_no_emb,
      (SELECT count(*) FROM v10_tags) AS tags
  `)
  console.log("\n--- Done ---")
  console.log(`embedded: ${embeddedCount}  skipped(unchanged): ${skippedCount}`)
  console.table(counts.rows[0])
  await pool.end()
}

main().catch(async (err) => {
  console.error("Ingestion failed:", err)
  await pool.end()
  process.exit(1)
})
