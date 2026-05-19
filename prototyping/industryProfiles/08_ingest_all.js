// 08_ingest_all.js — Run all ingestion steps for seeds
import pkg from 'pg';
const { Client } = pkg;
import fs from 'node:fs';
import path from 'node:path';
import dotenv from 'dotenv';
import { ingestGitHub } from './08a_ingest_github.js';
import { ingestWebsite } from './08b_ingest_website.js';
import { ingestInserat } from './08d_ingest_inserat.js';
import { ingestLinkedInCV } from './08c_ingest_linkedin_cv.js';
import { summarizeEntity } from './08e_summarize_entity.js';

dotenv.config();

const client = new Client({
    host: process.env.DB_HOST,
    port: process.env.DB_PORT,
    database: process.env.DB_NAME,
    user: process.env.DB_USER,
    password: process.env.DB_PASSWORD,
});

async function main() {
    await client.connect();
    const override = process.argv.includes('--override=true');
    
    try {
        if (override) {
            console.log('!!! Override enabled: Clearing existing entities and documents...');
            await client.query('TRUNCATE entities, documents RESTART IDENTITY CASCADE');
        }

        // 1. Ingest Inserate
        console.log('--- Ingesting Inserate ---');
        const insDir = path.join(process.cwd(), 'seeds', 'inserate');
        if (fs.existsSync(insDir)) {
            const files = fs.readdirSync(insDir).filter(f => f.endsWith('.json'));
            for (const f of files) {
                const inserat = JSON.parse(fs.readFileSync(path.join(insDir, f), 'utf8'));
                await ingestInserat(client, inserat);
            }
        }

        // 2. Ingest People
        console.log('\n--- Ingesting People ---');
        const people = JSON.parse(fs.readFileSync(path.join(process.cwd(), 'seeds', 'people.json'), 'utf8'));
        
        for (const person of people) {
            console.log(`\n> Processing ${person.name}...`);
            
            // Create entity
            await client.query(`
                INSERT INTO entities (id, type, name, canonical_location, scope)
                VALUES ($1, $2, $3, $4, $5)
                ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, scope = EXCLUDED.scope
            `, [person.id, 'person', person.name, person.location, person.scope]);

            // Ingest sources
            if (person.github) {
                try { await ingestGitHub(client, person.id, person.github); } catch (e) { console.error(`GitHub failed for ${person.name}:`, e.message); }
            }
            if (person.website) {
                try { await ingestWebsite(client, person.id, person.website); } catch (e) { console.error(`Website failed for ${person.name}:`, e.message); }
            }

            // Ingest manual files (txt, pdf)
            const firstName = person.name.split(' ')[0];
            const userDir = path.join(process.cwd(), 'users', firstName);
            if (fs.existsSync(userDir)) {
                const files = fs.readdirSync(userDir);
                for (const f of files) {
                    const filePath = path.join(userDir, f);
                    if (f.endsWith('.txt')) {
                        console.log(`[Manual] Ingesting text file: ${filePath}`);
                        const { getEmbedding, chunkText } = await import('./08_utils.js');
                        const rawText = fs.readFileSync(filePath, 'utf8');
                        const chunks = chunkText(rawText, 1000);
                        for (let i = 0; i < chunks.length; i++) {
                            const embedding = await getEmbedding(chunks[i]);
                            await client.query(`
                                INSERT INTO documents (id, entity_id, source_type, source_label, text, embedding)
                                VALUES ($1, $2, $3, $4, $5, $6)
                                ON CONFLICT (id) DO UPDATE SET text = EXCLUDED.text, embedding = EXCLUDED.embedding
                            `, [`manual-${person.id}-${f}-chunk-${i}`, person.id, 'linkedin_cv', `Source Archive · ${f} (${i+1})`, chunks[i], JSON.stringify(embedding)]);
                        }
                    } else if (f.endsWith('.pdf')) {
                        console.log(`[Manual] Ingesting PDF file: ${filePath}`);
                        await ingestLinkedInCV(client, person.id, filePath);
                    }
                }
            }
            
            // Summarize
            await summarizeEntity(client, person.id);
        }

        console.log('\nAll ingestion tasks completed.');
    } catch (err) {
        console.error('Ingestion failed:', err);
    } finally {
        await client.end();
    }
}

main();
