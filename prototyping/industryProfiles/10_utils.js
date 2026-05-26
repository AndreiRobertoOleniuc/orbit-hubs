// 10_utils.js — shared library for the BadenConnect v10 CLI.
// Embeddings stay local on Ollama (multilingual-e5-large); only the rationale
// moves to the Gemini Flash cloud API. Also: semantic chunking, geocode lookup,
// text hashing, the pg pool, and the one place all tuning constants live.

import crypto from "node:crypto"
import pkg from "pg"
import dotenv from "dotenv"
import { RecursiveCharacterTextSplitter } from "@langchain/textsplitters"

dotenv.config()

const { Pool } = pkg

// ── Shared config ──────────────────────────────────────────────────────────
export const TOP_K = 20 // Stage 1 retrieves a large pool; the list can show > 5
export const RRF_K = 60 // Reciprocal Rank Fusion constant
export const SCOPE_WEIGHTS = { fhnw: 1.3, aargau: 1.15, ch: 1.0 } // re-rank nudge, never a filter

export const EMBEDDING_MODEL = process.env.EMBEDDING_MODEL || "jeffh/intfloat-multilingual-e5-large:f16"
export const OLLAMA_URL = process.env.OLLAMA_URL || "http://localhost:11434"
// Use the rolling alias so a stale version string can never 404 (see spec §5.2).
export const GEMINI_MODEL = process.env.GEMINI_MODEL || "gemini-flash-latest"
const GEMINI_API_KEY = process.env.GEMINI_API_KEY

// Chunking targets: complete thoughts, not character counts (spec §5.1).
export const CHUNK_SIZE = 800
export const CHUNK_OVERLAP = 100
export const MAX_DOC_CHARS = 4000

// ── Postgres pool ────────────────────────────────────────────────────────────
export const pool = new Pool({
  host: process.env.DB_HOST,
  port: process.env.DB_PORT,
  database: process.env.DB_NAME,
  user: process.env.DB_USER,
  password: process.env.DB_PASSWORD,
})

// ── Embeddings (local Ollama) ────────────────────────────────────────────────
// e5 models need an instruction prefix: "passage:" for stored docs, "query:" for searches.
export async function embed(text, kind = "passage") {
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), 60000)
  try {
    const res = await fetch(`${OLLAMA_URL}/api/embeddings`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        model: EMBEDDING_MODEL,
        prompt: `${kind}: ${text}`,
        keep_alive: "30m",
      }),
      signal: controller.signal,
    })
    if (!res.ok) throw new Error(`embedding failed: ${res.status} ${res.statusText}`)
    const data = await res.json()
    if (!Array.isArray(data.embedding)) throw new Error("embedding response had no vector")
    return data.embedding
  } finally {
    clearTimeout(timeout)
  }
}

export const embedPassage = (text) => embed(text, "passage")
export const embedQuery = (text) => embed(text, "query")

// ── Semantic chunking ────────────────────────────────────────────────────────
// Recursive splitter: paragraph -> line -> space -> char. Keeps thoughts intact.
const splitter = new RecursiveCharacterTextSplitter({
  chunkSize: CHUNK_SIZE,
  chunkOverlap: CHUNK_OVERLAP,
  separators: ["\n\n", "\n", ". ", " ", ""],
})

export async function semanticChunks(text) {
  const clean = (text || "").replace(/\r/g, "").trim()
  if (!clean) return []
  const chunks = await splitter.splitText(clean)
  // Hard cap each chunk so an oversized paragraph can't blow past the column budget.
  return chunks.map((c) => c.slice(0, MAX_DOC_CHARS)).filter((c) => c.trim().length > 0)
}

// ── Text hash (skip re-embedding unchanged content) ──────────────────────────
export function textHash(text) {
  return crypto.createHash("sha256").update(text || "").digest("hex")
}

// ── Geocode (hardcoded Aargau lookup; harvester yields town names, map needs lat/lng) ──
const GEO = {
  baden: [47.4767, 8.3065],
  brugg: [47.4846, 8.2076],
  wettingen: [47.4667, 8.3206],
  aarau: [47.3909, 8.0456],
  lenzburg: [47.3884, 8.1764],
  windisch: [47.4795, 8.2186],
  zürich: [47.3769, 8.5417],
  zurich: [47.3769, 8.5417],
  bern: [46.948, 7.4474],
  olten: [47.3501, 7.9036],
}

export function geocode(location) {
  if (!location) return { lat: null, lng: null }
  const key = location.trim().toLowerCase()
  const hit = GEO[key]
  return hit ? { lat: hit[0], lng: hit[1] } : { lat: null, lng: null }
}

// ── Gemini Flash rationale ────────────────────────────────────────────────────
// Returns the contract { warum, evidence:[{ref_id,label,url}] }. Every ref_id is
// validated against the IDs actually passed in; hallucinated ones are dropped.
// Throws on any API/parse failure so the caller can fall back gracefully.
const RATIONALE_SCHEMA = {
  type: "OBJECT",
  properties: {
    warum: { type: "STRING" },
    evidence: {
      type: "ARRAY",
      items: {
        type: "OBJECT",
        properties: {
          ref_id: { type: "STRING" },
          label: { type: "STRING" },
          url: { type: "STRING" },
        },
        required: ["ref_id", "label"],
        propertyOrdering: ["ref_id", "label", "url"],
      },
    },
  },
  required: ["warum", "evidence"],
  propertyOrdering: ["warum", "evidence"],
}

// Plain-text Gemini call, used for the (non-embedded) entity card summary.
export async function geminiText(prompt) {
  if (!GEMINI_API_KEY) throw new Error("GEMINI_API_KEY not set")
  const url = `https://generativelanguage.googleapis.com/v1beta/models/${GEMINI_MODEL}:generateContent?key=${GEMINI_API_KEY}`
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), 20000)
  try {
    const res = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        contents: [{ role: "user", parts: [{ text: prompt }] }],
        generationConfig: { temperature: 0.3 },
      }),
      signal: controller.signal,
    })
    if (!res.ok) throw new Error(`Gemini failed: ${res.status}`)
    const data = await res.json()
    const raw = data?.candidates?.[0]?.content?.parts?.[0]?.text
    if (!raw) throw new Error("Gemini returned no text")
    return raw.trim()
  } finally {
    clearTimeout(timeout)
  }
}

export async function generateRationale({ query, candidate, evidence }) {
  if (!GEMINI_API_KEY) throw new Error("GEMINI_API_KEY not set")

  const allowedIds = new Set(evidence.map((e) => e.id))
  const evidenceBlock = evidence
    .map((e) => `[${e.id}] (${e.label})\n${(e.text || "").slice(0, 1200)}`)
    .join("\n\n")

  const prompt = `Du erklärst, warum dieses Ergebnis zur Suchanfrage einer Person passt, die jemanden in der Nähe sucht.

Suchanfrage:
"${query}"

Ergebnis:
${candidate.type} · ${candidate.name}${candidate.headline ? ` · ${candidate.headline}` : ""}${candidate.location ? ` · ${candidate.location}` : ""}

Belege (du DARFST und SOLLST diese per ID zitieren):
${evidenceBlock}

Regeln:
- Antworte auf Deutsch, 2-3 Sätze im Feld "warum".
- Belege deine Aussage, indem du im Feld "evidence" nur die oben aufgeführten IDs angibst.
- Erfinde KEINE IDs; nur die aufgeführten sind erlaubt.
- "label" und "url" je Beleg aus dem Kontext übernehmen.`

  const url = `https://generativelanguage.googleapis.com/v1beta/models/${GEMINI_MODEL}:generateContent?key=${GEMINI_API_KEY}`
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), 20000)
  try {
    const res = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        contents: [{ role: "user", parts: [{ text: prompt }] }],
        generationConfig: {
          responseMimeType: "application/json",
          responseSchema: RATIONALE_SCHEMA,
          temperature: 0.2,
        },
      }),
      signal: controller.signal,
    })
    if (!res.ok) throw new Error(`Gemini failed: ${res.status} ${await res.text()}`)
    const data = await res.json()
    const raw = data?.candidates?.[0]?.content?.parts?.[0]?.text
    if (!raw) throw new Error("Gemini returned no text")
    const parsed = JSON.parse(raw)
    // Validate citations against the set actually passed in.
    parsed.evidence = (parsed.evidence || []).filter((e) => allowedIds.has(e.ref_id))
    return parsed
  } finally {
    clearTimeout(timeout)
  }
}
