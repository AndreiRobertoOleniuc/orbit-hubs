// 08d_ingest_inserat.js — Ingest Inserate (Job postings)
import pkg from 'pg';
const { Client } = pkg;
import fs from 'node:fs';
import path from 'node:path';
import dotenv from 'dotenv';
import { getEmbedding } from './08_utils.js';

dotenv.config();

const client = new Client({
    host: process.env.DB_HOST,
    port: process.env.DB_PORT,
    database: process.env.DB_NAME,
    user: process.env.DB_USER,
    password: process.env.DB_PASSWORD,
});

export async function ingestInserat(client, inserat) {
    console.log(`[Inserat] Ingesting ${inserat.id}: ${inserat.title}...`);
    
    // 1. Create/Update Entity
    await client.query(`
        INSERT INTO entities (id, type, name, canonical_location, scope)
        VALUES ($1, $2, $3, $4, $5)
        ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, scope = EXCLUDED.scope
    `, [inserat.id, 'inserat', inserat.title, inserat.location || 'Baden', inserat.scope || 'aargau']);

    // 2. Create/Update Document
    const text = `${inserat.title}\n\n${inserat.body}\n\nValid until: ${inserat.valid_until}. Honorar: ${inserat.honorar_mode}.`;
    const embedding = await getEmbedding(text);
    
    await client.query(`
        INSERT INTO documents (id, entity_id, source_type, source_url, source_label, text, embedding, meta)
        VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
        ON CONFLICT (id) DO UPDATE SET text = EXCLUDED.text, embedding = EXCLUDED.embedding, fetched_at = NOW()
    `, [
        `doc-${inserat.id}`,
        inserat.id,
        'inserat_body',
        `internal://inserate/${inserat.id}`,
        `Inserat · ${inserat.title}`,
        text,
        JSON.stringify(embedding),
        JSON.stringify({ owner: inserat.owner_entity_id, valid_until: inserat.valid_until })
    ]);
}

// Seed helper
async function seedFromDir(client, dir) {
    if (!fs.existsSync(dir)) {
        console.warn(`Dir ${dir} not found, skipping.`);
        return;
    }
    const files = fs.readdirSync(dir).filter(f => f.endsWith('.json'));
    for (const f of files) {
        const inserat = JSON.parse(fs.readFileSync(path.join(dir, f), 'utf8'));
        await ingestInserat(client, inserat);
    }
}

// CLI usage
if (process.argv[1].endsWith('08d_ingest_inserat.js')) {
    await client.connect();
    try {
        const seedDir = path.join(process.cwd(), 'seeds', 'inserate');
        await seedFromDir(client, seedDir);
        console.log('Done.');
    } catch (err) {
        console.error(err);
    } finally {
        await client.end();
    }
}
