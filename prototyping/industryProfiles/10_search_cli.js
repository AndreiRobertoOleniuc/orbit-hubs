// 10_search_cli.js — the decoupled three-stage discovery flow.
//
//   Stage 1  Retrieval   dense (pgvector <=>) + lexical (FTS) over BOTH
//                         documents and inserate, fused with RRF (k=60).
//   Stage 2  Re-rank      entity rollup + scope weights + active-inserat filter.
//                         The ranked list renders NOW — no LLM in the hot path.
//   Stage 3  Rationale    lazy: typing a result number asks Gemini Flash for a
//                         cited German rationale for just that one result.
//
// Loop commands:  <N> rationale for result N · more · new · q
// One-shot:       node 10_search_cli.js "embedded firmware security review"

import readline from "node:readline"
import {
  pool,
  embedQuery,
  generateRationale,
  TOP_K,
  RRF_K,
  SCOPE_WEIGHTS,
  GEMINI_MODEL,
} from "./10_utils.js"

const PAGE_SIZE = Number(process.env.PAGE_SIZE) || 10
// Superscript digits are NOT contiguous in Unicode (¹²³ live at U+00B9/B2/B3),
// so map them explicitly rather than offsetting a char code.
const SUP = ["¹", "²", "³", "⁴", "⁵", "⁶", "⁷", "⁸", "⁹"]
const sup = (i) => SUP[i] || `[${i + 1}]`

// ── ANSI helpers ──────────────────────────────────────────────────────────────
const c = {
  b: (s) => `\x1b[1m${s}\x1b[0m`,
  dim: (s) => `\x1b[2m${s}\x1b[0m`,
  cyan: (s) => `\x1b[36m${s}\x1b[0m`,
  green: (s) => `\x1b[32m${s}\x1b[0m`,
  yellow: (s) => `\x1b[33m${s}\x1b[0m`,
}

// ── Stage 1: retrieval + RRF ────────────────────────────────────────────────
function rrfMerge(lists) {
  // lists: array of arrays of {id, rank}. Returns Map id -> summed 1/(k+rank).
  const scores = new Map()
  for (const list of lists) {
    for (const { id, rank } of list) {
      scores.set(id, (scores.get(id) || 0) + 1 / (RRF_K + Number(rank)))
    }
  }
  return scores
}

async function retrieve(query) {
  const qvec = JSON.stringify(await embedQuery(query))
  const insFilter = `status = 'active' AND (valid_until IS NULL OR valid_until >= CURRENT_DATE)`

  const [denseDocs, lexDocs, denseIns, lexIns] = await Promise.all([
    pool.query(
      `SELECT id, entity_id, ROW_NUMBER() OVER (ORDER BY embedding <=> $1::vector) AS rank
       FROM v10_documents ORDER BY embedding <=> $1::vector LIMIT 100`,
      [qvec]
    ),
    pool.query(
      `SELECT id, entity_id, ROW_NUMBER() OVER (ORDER BY ts_rank_cd(tsv, plainto_tsquery('simple',$1)) DESC) AS rank
       FROM v10_documents WHERE tsv @@ plainto_tsquery('simple',$1)
       ORDER BY ts_rank_cd(tsv, plainto_tsquery('simple',$1)) DESC LIMIT 100`,
      [query]
    ),
    pool.query(
      `SELECT id, ROW_NUMBER() OVER (ORDER BY embedding <=> $1::vector) AS rank
       FROM v10_inserate WHERE ${insFilter} ORDER BY embedding <=> $1::vector LIMIT 100`,
      [qvec]
    ),
    pool.query(
      `SELECT id, ROW_NUMBER() OVER (ORDER BY ts_rank_cd(tsv, plainto_tsquery('simple',$1)) DESC) AS rank
       FROM v10_inserate WHERE ${insFilter} AND tsv @@ plainto_tsquery('simple',$1)
       ORDER BY ts_rank_cd(tsv, plainto_tsquery('simple',$1)) DESC LIMIT 100`,
      [query]
    ),
  ])

  const docScores = rrfMerge([denseDocs.rows, lexDocs.rows]) // id -> score
  const insScores = rrfMerge([denseIns.rows, lexIns.rows])
  const docEntity = new Map() // docId -> entityId
  for (const r of [...denseDocs.rows, ...lexDocs.rows]) docEntity.set(r.id, r.entity_id)

  return { docScores, insScores, docEntity }
}

// ── Stage 2: rollup + scope weighting ────────────────────────────────────────
async function rerank(query) {
  const { docScores, insScores, docEntity } = await retrieve(query)

  // Roll documents up to entities; keep their best docs as evidence candidates.
  const entityScore = new Map()
  const entityDocs = new Map() // entityId -> [{docId, score}]
  for (const [docId, score] of docScores) {
    const eid = docEntity.get(docId)
    if (!eid) continue
    entityScore.set(eid, (entityScore.get(eid) || 0) + score)
    if (!entityDocs.has(eid)) entityDocs.set(eid, [])
    entityDocs.get(eid).push({ docId, score })
  }

  const results = []

  // Entity results.
  if (entityScore.size) {
    const { rows: ents } = await pool.query(
      `SELECT id, type, name, headline, location, scope, summary FROM v10_entities WHERE id = ANY($1)`,
      [[...entityScore.keys()]]
    )
    for (const e of ents) {
      const weight = SCOPE_WEIGHTS[e.scope] ?? 1.0
      // Score the entity by its best 3 pieces of evidence (the ones we cite),
      // not by sheer document volume — otherwise a prolific profile out-ranks a
      // sharply relevant one. base = sum of top-3 doc RRF scores.
      const top = entityDocs.get(e.id).sort((a, b) => b.score - a.score).slice(0, 3)
      const base = top.reduce((s, d) => s + d.score, 0)
      const docIds = top.map((d) => d.docId)
      results.push({
        kind: "entity",
        id: e.id,
        type: e.type,
        title: e.name,
        headline: e.headline,
        location: e.location,
        scope: e.scope,
        summary: e.summary,
        base,
        score: base * weight,
        docIds,
      })
    }
  }

  // Inserat results (already filtered to active + non-expired in Stage 1).
  if (insScores.size) {
    const { rows: insrows } = await pool.query(
      `SELECT id, kind, title, description, owner_entity_id, region, location, honorar_text
       FROM v10_inserate WHERE id = ANY($1)`,
      [[...insScores.keys()]]
    )
    for (const i of insrows) {
      const weight = SCOPE_WEIGHTS[i.region] ?? 1.0
      results.push({
        kind: "inserat",
        id: i.id,
        type: "inserat",
        title: i.title,
        headline: `${i.kind} · ${i.honorar_text || ""}`.trim(),
        location: i.location,
        scope: i.region,
        ownerEntityId: i.owner_entity_id,
        description: i.description,
        base: insScores.get(i.id),
        score: insScores.get(i.id) * weight,
      })
    }
  }

  results.sort((a, b) => b.score - a.score)
  return results.slice(0, TOP_K)
}

// ── Stage 3: lazy rationale ──────────────────────────────────────────────────
// Assemble the evidence set the rationale is allowed to cite, then call Gemini.
async function assembleEvidence(result) {
  const evidence = []

  if (result.kind === "entity") {
    if (result.docIds?.length) {
      const { rows } = await pool.query(
        `SELECT id, source_label AS label, source_url AS url, text FROM v10_documents WHERE id = ANY($1)`,
        [result.docIds]
      )
      // preserve ranked order
      for (const id of result.docIds) {
        const r = rows.find((x) => x.id === id)
        if (r) evidence.push(r)
      }
    }
    // If this entity owns an active inserat, offer it as evidence too.
    const { rows: owned } = await pool.query(
      `SELECT id, title, description, source_url FROM (
         SELECT id, title, description, ('internal://inserate/' || id) AS source_url
         FROM v10_inserate
         WHERE owner_entity_id = $1 AND status = 'active'
           AND (valid_until IS NULL OR valid_until >= CURRENT_DATE)
       ) t LIMIT 2`,
      [result.id]
    )
    for (const o of owned) {
      evidence.push({ id: o.id, label: `Inserat · ${o.title}`, url: o.source_url, text: o.description })
    }
  } else {
    // Inserat result: the post itself, plus up to 2 of its owner's documents.
    evidence.push({
      id: result.id,
      label: `Inserat · ${result.title}`,
      url: `internal://inserate/${result.id}`,
      text: result.description,
    })
    if (result.ownerEntityId) {
      const { rows } = await pool.query(
        `SELECT id, source_label AS label, source_url AS url, text
         FROM v10_documents WHERE entity_id = $1 ORDER BY char_length(text) DESC LIMIT 2`,
        [result.ownerEntityId]
      )
      evidence.push(...rows)
    }
  }
  return evidence
}

async function showRationale(query, result) {
  const evidence = await assembleEvidence(result)
  process.stdout.write(c.dim(`  … Gemini (${GEMINI_MODEL}) denkt nach …\r`))

  let rationale
  try {
    rationale = await generateRationale({
      query,
      candidate: { type: result.type, name: result.title, headline: result.headline, location: result.location },
      evidence,
    })
  } catch (err) {
    console.log(`\n  ${c.yellow("⚠ Keine Begründung verfügbar")} (${err.message}).`)
    if (evidence.length) {
      console.log(c.dim("  Belege:"))
      evidence.forEach((e) => console.log(`    · ${e.label}  ${c.dim(e.id)}`))
    }
    console.log("")
    return
  }

  // Render rationale with footnote markers ¹²³ mapping to the cited evidence.
  let why = rationale.warum || "—"
  const cited = rationale.evidence || []
  cited.forEach((ev, i) => {
    why = why.split(`[${ev.ref_id}]`).join(c.b(sup(i)))
  })

  console.log(`\n  ${c.b("Warum?")} ${why}\n`)
  if (cited.length) {
    console.log(c.dim("  EVIDENZ"))
    cited.forEach((ev, i) => {
      console.log(`    ${sup(i)}  ${ev.label}  ${c.dim(ev.url || ev.ref_id)}`)
    })
  } else {
    console.log(c.dim("  (keine zitierten Belege)"))
  }
  console.log("")
}

// ── Rendering the instant list ────────────────────────────────────────────────
const TYPE_BADGE = { person: "PERSON ", company: "FIRMA  ", inserat: "INSERAT" }

function renderRows(results, from, to) {
  for (let i = from; i < to && i < results.length; i++) {
    const r = results[i]
    const badge = c.cyan(TYPE_BADGE[r.type] || r.type)
    const loc = r.location ? `${r.location}` : "—"
    const head = r.headline ? c.dim(` · ${r.headline}`) : ""
    console.log(
      `${c.b(String(i + 1).padStart(2))}  ${badge}  ${c.b(r.title)}${head}`
    )
    console.log(
      `      ${loc} · ⌖ ${r.scope.toUpperCase()} · ${c.dim("score " + r.score.toFixed(4))}`
    )
  }
}

// ── REPL ─────────────────────────────────────────────────────────────────────
async function main() {
  const rl = readline.createInterface({ input: process.stdin, output: process.stdout })

  // Line queue: works for an interactive TTY and for piped stdin alike. `ask`
  // pulls the next buffered line or waits; once stdin closes, pending/future
  // asks resolve to null so the loop ends gracefully (no USE_AFTER_CLOSE).
  const lineBuf = []
  const waiters = []
  let closed = false
  rl.on("line", (l) => {
    if (waiters.length) waiters.shift()(l)
    else lineBuf.push(l)
  })
  rl.on("close", () => {
    closed = true
    while (waiters.length) waiters.shift()(null)
  })
  const ask = (q) => {
    if (q) process.stdout.write(q)
    if (lineBuf.length) return Promise.resolve(lineBuf.shift())
    if (closed) return Promise.resolve(null)
    return new Promise((res) => waiters.push(res))
  }

  console.log(c.b("\nBadenConnect v10 — Discovery CLI"))
  console.log(c.dim("Frag in natürlicher Sprache, was du suchst.\n"))

  let query = process.argv.slice(2).join(" ").trim()
  let results = []
  let shown = 0

  async function runQuery(q) {
    query = q
    const t0 = Date.now()
    results = await rerank(q)
    const ms = Date.now() - t0
    console.log(`\n${c.dim(`Stage 1+2 in ${ms} ms · ${results.length} Treffer (ohne LLM):`)}\n`)
    if (results.length === 0) {
      console.log("  Keine Ergebnisse.\n")
      shown = 0
      return
    }
    shown = Math.min(PAGE_SIZE, results.length)
    renderRows(results, 0, shown)
    console.log(c.dim(`\n  [Zahl] = Begründung · more = weitere · new = neue Suche · q = Ende`))
  }

  if (!query) {
    const first = await ask(c.green("Suche› "))
    query = (first || "").trim()
  }
  if (query) await runQuery(query)

  for (;;) {
    const raw = await ask(c.green("\n› "))
    if (raw === null) break // stdin closed
    const line = raw.trim()
    if (line === "q" || line === ":q") break
    if (line === "") continue
    if (line === "new") {
      const q = (await ask(c.green("Suche› "))) || ""
      if (q.trim()) await runQuery(q.trim())
      continue
    }
    if (line === "more") {
      if (shown >= results.length) {
        console.log(c.dim("  Keine weiteren Ergebnisse."))
      } else {
        const to = Math.min(shown + PAGE_SIZE, results.length)
        renderRows(results, shown, to)
        shown = to
      }
      continue
    }
    const n = Number(line)
    if (Number.isInteger(n) && n >= 1 && n <= results.length) {
      await showRationale(query, results[n - 1])
    } else {
      console.log(c.dim("  Unbekannter Befehl. Nutze [Zahl] / more / new / q."))
    }
  }

  rl.close()
  await pool.end()
  console.log(c.dim("\nTschüss.\n"))
}

main().catch(async (err) => {
  console.error("CLI failed:", err)
  await pool.end()
  process.exit(1)
})
