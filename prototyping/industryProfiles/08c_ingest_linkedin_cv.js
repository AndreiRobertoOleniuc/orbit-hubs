// 08c_ingest_linkedin_cv.js — Ingest LinkedIn CV PDF
import pkg from 'pg';
const { Client } = pkg;
import pdf from 'pdf-parse/lib/pdf-parse.js';
import fs from 'node:fs';
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

export async function ingestLinkedInCV(entityId, pdfPath) {
    console.log(`[LinkedIn CV] Ingesting ${pdfPath} for ${entityId}...`);
    
    const dataBuffer = fs.readFileSync(pdfPath);
    const data = await pdf(dataBuffer);
    
    const fullText = data.text;
    
    // Simple chunking by common section headers
    const sections = fullText.split(/\n(?=(?:Experience|Education|Skills|Languages|Summary|Projects|Honors|Certifications|Volunteering|Interests)\n?)/i);
    
    for (let i = 0; i < sections.length; i++) {
        let sectionText = sections[i].trim();
        if (!sectionText) continue;
        
        // Try to identify the section header
        const lines = sectionText.split('\n');
        const header = lines[0].trim();
        const label = `LinkedIn CV · ${header || 'Section ' + (i+1)}`;
        
        console.log(`  - Ingesting section: ${header || 'Introduction'}`);
        
        const embedding = await getEmbedding(sectionText);
        const sectionId = `cv-${entityId}-${header.toLowerCase().replace(/[^a-z0-9]/g, '-') || 'intro-' + i}`;
        
        await client.query(`
            INSERT INTO documents (id, entity_id, source_type, source_url, source_label, text, embedding, meta)
            VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
            ON CONFLICT (id) DO UPDATE SET text = EXCLUDED.text, embedding = EXCLUDED.embedding, fetched_at = NOW()
        `, [
            sectionId,
            entityId,
            'linkedin_cv',
            `linkedin-cv://uploaded/${pdfPath.split('/').pop()}`,
            label,
            sectionText,
            JSON.stringify(embedding),
            JSON.stringify({ section: header })
        ]);
    }
}

// CLI usage
if (process.argv[1].endsWith('08c_ingest_linkedin_cv.js')) {
    const pdfPath = process.argv[2];
    const entityId = process.argv[3];
    if (!pdfPath || !entityId) {
        console.error('Usage: node 08c_ingest_linkedin_cv.js <pdfPath> <entityId>');
        process.exit(1);
    }
    
    await client.connect();
    try {
        await ingestLinkedInCV(entityId, pdfPath);
        console.log('Done.');
    } catch (err) {
        console.error(err);
    } finally {
        await client.end();
    }
}
