// 08c_ingest_linkedin_cv.js — Ingest LinkedIn CV PDF
import pkg from 'pg';
const { Client } = pkg;
import { PDFParse } from 'pdf-parse';
import fs from 'node:fs';
import dotenv from 'dotenv';
import { getEmbedding } from './08_utils.js';

dotenv.config();

const client = new Client({
    host: "127.0.0.1",
    port: process.env.DB_PORT,
    database: process.env.DB_NAME,
    user: process.env.DB_USER,
    password: process.env.DB_PASSWORD,
});

export async function ingestLinkedInCV(client, entityId, pdfPath) {
    console.log(`[LinkedIn CV] Ingesting ${pdfPath} for ${entityId}...`);
    
    const dataBuffer = fs.readFileSync(pdfPath);
    const parser = new PDFParse({ data: dataBuffer });
    const result = await parser.getText();
    await parser.destroy();
    
    const fullText = result.text;
    
    // Multilingual chunking (EN, DE, FR, ES, IT)
    const sectionPattern = /\n(?=(?:Experience|Berufserfahrung|Expérience|Experiencia|Esperienza|Education|Ausbildung|Formation|Educación|Formazione|Skills|Kenntnisse|Compétences|Habilidades|Competenze|Languages|Sprachen|Langues|Idiomas|Lingue|Summary|Zusammenfassung|Résumé|Resumen|Riepilogo|Projects|Projekte|Proyectos|Progetti|Honors|Certifications|Zertifikate|Certificaciones|Certificazioni)\n?)/i;
    const sections = fullText.split(sectionPattern);
    
    const { getEmbedding, chunkText } = await import('./08_utils.js');
    
    for (let i = 0; i < sections.length; i++) {
        let sectionRaw = sections[i].trim();
        if (!sectionRaw) continue;
        
        const lines = sectionRaw.split('\n');
        const header = lines[0].trim();
        const chunks = chunkText(sectionRaw, 1000);
        
        for (let j = 0; j < chunks.length; j++) {
            console.log(`  - Ingesting section: ${header || 'Introduction'} (Part ${j+1})`);
            const embedding = await getEmbedding(chunks[j]);
            const sectionId = `cv-${entityId}-${header.toLowerCase().replace(/[^a-z0-9]/g, '-') || 'intro'}-chunk-${i}-${j}`;
            
            await client.query(`
                INSERT INTO documents (id, entity_id, source_type, source_url, source_label, text, embedding, meta)
                VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
                ON CONFLICT (id) DO UPDATE SET text = EXCLUDED.text, embedding = EXCLUDED.embedding, fetched_at = NOW()
            `, [
                sectionId,
                entityId,
                'linkedin_cv',
                `linkedin-cv://uploaded/${pdfPath.split('/').pop()}`,
                `LinkedIn CV · ${header || 'Section'} ${j > 0 ? `(${j+1})` : ''}`,
                chunks[j],
                JSON.stringify(embedding),
                JSON.stringify({ section: header, part: j })
            ]);
        }
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
