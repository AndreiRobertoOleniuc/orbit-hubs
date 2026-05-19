// 08b_ingest_website.js — Ingest personal website content
import pkg from 'pg';
const { Client } = pkg;
import { JSDOM } from 'jsdom';
import { Readability } from '@mozilla/readability';
import dotenv from 'dotenv';
import { getEmbedding, truncate, chunkText } from './08_utils.js';

dotenv.config();

const client = new Client({
    host: "127.0.0.1",
    port: process.env.DB_PORT,
    database: process.env.DB_NAME,
    user: process.env.DB_USER,
    password: process.env.DB_PASSWORD,
});

export async function ingestWebsite(client, entityId, url) {
    console.log(`[Website] Ingesting ${url} for ${entityId}...`);
    
    const res = await fetch(url, {
        headers: {
            'User-Agent': 'BadenConnect-Prototype/0.1 (contact@example.com)'
        }
    });
    if (!res.ok) throw new Error(`Failed to fetch ${url}: ${res.status}`);
    
    const html = await res.text();
    const doc = new JSDOM(html, { url });
    
    // User requested simpler innerText-like extraction
    const rawText = doc.window.document.body.textContent.replaceAll("\n", " ").replace(/\s+/g, " ").trim();
    const chunks = chunkText(rawText);
    
    const urlId = url.replace(/[^a-z0-9]/gi, '-').toLowerCase();
    
    for (let i = 0; i < chunks.length; i++) {
        console.log(`  - Ingesting chunk ${i+1}/${chunks.length}...`);
        const embedding = await getEmbedding(chunks[i]);
        
        await client.query(`
            INSERT INTO documents (id, entity_id, source_type, source_url, source_label, text, embedding, meta)
            VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
            ON CONFLICT (id) DO UPDATE SET text = EXCLUDED.text, embedding = EXCLUDED.embedding, fetched_at = NOW()
        `, [
            `web-${urlId}-chunk-${i}`,
            entityId,
            'website_page',
            url,
            `Website · ${doc.window.document.title || url}${i > 0 ? ` (${i+1})` : ''}`,
            chunks[i],
            JSON.stringify(embedding),
            JSON.stringify({ title: doc.window.document.title, chunk: i })
        ]);
    }
    
    console.log(`  - Ingested ${chunks.length} chunks for: ${doc.window.document.title}`);
}

// CLI usage
if (process.argv[1].endsWith('08b_ingest_website.js')) {
    const url = process.argv[2];
    const entityId = process.argv[3];
    if (!url || !entityId) {
        console.error('Usage: node 08b_ingest_website.js <url> <entityId>');
        process.exit(1);
    }
    
    await client.connect();
    try {
        await ingestWebsite(client, entityId, url);
        console.log('Done.');
    } catch (err) {
        console.error(err);
    } finally {
        await client.end();
    }
}
