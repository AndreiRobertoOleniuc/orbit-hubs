// 08_search_cli.js — BadenConnect v8 Search CLI
import readline from "node:readline";
import pkg from "pg";
const { Client } = pkg;
import dotenv from 'dotenv';
import { getEmbedding, getRationale } from './08_utils.js';

dotenv.config();

const client = new Client({
    host: process.env.DB_HOST,
    port: process.env.DB_PORT,
    database: process.env.DB_NAME,
    user: process.env.DB_USER,
    password: process.env.DB_PASSWORD,
});

const TOP_K = 5;
const RRF_K = 60;
const SCOPE_WEIGHTS = { 'fhnw': 1.3, 'aargau': 1.15, 'ch': 1.0 };

async function hybridSearch(query) {
    const vec = await getEmbedding(query);
    
    // 1. Dense candidates
    const { rows: dense } = await client.query(`
        SELECT id, entity_id, 1.0 / (${RRF_K} + ROW_NUMBER() OVER (ORDER BY embedding <=> $1::vector)) as rrf_score
        FROM documents
        ORDER BY embedding <=> $1::vector
        LIMIT 100
    `, [JSON.stringify(vec)]);

    // 2. Lexical candidates
    const { rows: lexical } = await client.query(`
        SELECT id, entity_id, 1.0 / (${RRF_K} + ROW_NUMBER() OVER (ORDER BY ts_rank_cd(tsv, plainto_tsquery('simple', $1)) DESC)) as rrf_score
        FROM documents
        WHERE tsv @@ plainto_tsquery('simple', $1)
        ORDER BY ts_rank_cd(tsv, plainto_tsquery('simple', $1)) DESC
        LIMIT 100
    `, [query]);

    // 3. Merge at document level
    const docScores = {};
    [...dense, ...lexical].forEach(row => {
        docScores[row.id] = (docScores[row.id] || 0) + parseFloat(row.rrf_score);
    });

    // 4. Group by entity and sum scores, keep best documents per entity
    const entityScores = {};
    const entityDocs = {}; // entityId -> [docIds]
    
    Object.entries(docScores).forEach(([docId, score]) => {
        const docInfo = [...dense, ...lexical].find(d => d.id === docId);
        const eid = docInfo.entity_id;
        entityScores[eid] = (entityScores[eid] || 0) + score;
        if (!entityDocs[eid]) entityDocs[eid] = [];
        entityDocs[eid].push(docId);
    });

    // 5. Fetch entity details and re-rank
    const eids = Object.keys(entityScores);
    if (eids.length === 0) return [];

    const { rows: entities } = await client.query(`
        SELECT id, name, type, scope, summary, canonical_location
        FROM entities
        WHERE id = ANY($1)
    `, [eids]);

    const results = entities.map(e => {
        const baseScore = entityScores[e.id];
        const weight = SCOPE_WEIGHTS[e.scope] || 1.0;
        return { ...e, score: baseScore * weight, docIds: entityDocs[e.id].slice(0, 3) };
    });

    return results.sort((a, b) => b.score - a.score).slice(0, TOP_K);
}

async function generateRationale(query, entity) {
    // Fetch document texts for the top 3 docs
    const { rows: docs } = await client.query(`
        SELECT id, source_label, text 
        FROM documents 
        WHERE id = ANY($1)
    `, [entity.docIds]);

    const docContext = docs.map((d, i) => `[${i+1}] id=${d.id} · ${d.source_label}\n${d.text}`).join('\n\n');

    const prompt = `Du analysierst, ob diese Entität zur Suchanfrage passt.

Suchanfrage:
"${query}"

Entität:
${entity.type} · ${entity.name} · ${entity.scope}

Verfügbare Dokumente (du DARFST und SOLLST diese per ID zitieren):
${docContext}

Antworte als JSON, exakt dieses Schema:
{
  "rolle": "max 6 Wörter",
  "kontext": "Firma/Studium/Selbstständig, max 6 Wörter",
  "skill_tags": ["max 3 konkrete Tags zur Anfrage"],
  "warum": "2 Sätze. Belege MÜSSEN per [doc_id] zitiert werden, z.B. 'Marc hat vor 3 Tagen ein Inserat veröffentlicht [ins-marc-secrev]'.",
  "evidence": ["doc_id", "doc_id", ...] // die tatsächlich zitierten IDs, in Reihenfolge des Auftretens
}

Wichtig: erfinde KEINE Dokument-IDs. Nur die oben aufgeführten sind erlaubt.`;

    try {
        const rationale = await getRationale(prompt);
        // Validate IDs
        const validIds = docs.map(d => d.id);
        rationale.evidence = (rationale.evidence || []).filter(id => validIds.includes(id));
        
        // Return rationale and the actual doc details for rendering
        return { ...rationale, docs };
    } catch (err) {
        console.error(`Rationale failed for ${entity.id}:`, err);
        return null;
    }
}

function renderMatch(index, entity, rationale) {
    console.log(`\x1b[1m${index + 1}\x1b[0m   [${entity.name.substring(0, 2).toUpperCase()}]  \x1b[1m${entity.name}\x1b[0m   • verifiziert`);
    console.log(`         ${rationale?.rolle || entity.type} · ${entity.canonical_location || 'Unbekannt'} · ⌖ ${entity.scope.toUpperCase()}`);
    console.log(`         ${(rationale?.skill_tags || []).map(t => `[\x1b[36m${t}\x1b[0m]`).join('  ')}`);
    console.log('');
    console.log(`         ▼ Warum diese Person?`);
    
    let why = rationale?.warum || entity.summary || "Keine Begründung verfügbar.";
    
    // Footnote replacement
    const citations = rationale?.evidence || [];
    citations.forEach((id, i) => {
        const symbol = String.fromCharCode(0x00B9 + i); // ¹, ², ³...
        why = why.replace(`[${id}]`, `\x1b[1m${symbol}\x1b[0m`);
    });
    
    console.log(`         ${why}`);
    console.log('');
    
    if (citations.length > 0) {
        console.log(`         \x1b[2mEVIDENZ\x1b[0m`);
        citations.forEach((id, i) => {
            const doc = rationale.docs.find(d => d.id === id);
            const symbol = String.fromCharCode(0x00B9 + i);
            console.log(`         ${symbol}  ${doc.source_label}  · ${doc.id}`);
        });
    }
    console.log('\n' + '─'.repeat(60) + '\n');
}

const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout,
    prompt: "\x1b[32mquery›\x1b[0m ",
});

async function main() {
    await client.connect();
    console.log("\x1b[1mBadenConnect v8 CLI Search\x1b[0m (:q to exit)\n");
    rl.prompt();

    rl.on("line", async (line) => {
        const q = line.trim();
        if (q === ":q") process.exit(0);
        if (!q) { rl.prompt(); return; }

        console.log(`\n\x1b[2mSuche nach "${q}"...\x1b[0m\n`);
        
        try {
            const results = await hybridSearch(q);
            if (results.length === 0) {
                console.log("Keine Ergebnisse gefunden.\n");
            } else {
                for (let i = 0; i < results.length; i++) {
                    const rationale = await generateRationale(q, results[i]);
                    renderMatch(i, results[i], rationale);
                }
            }
        } catch (err) {
            console.error("Fehler bei der Suche:", err);
        }
        
        rl.prompt();
    });
}

main();
