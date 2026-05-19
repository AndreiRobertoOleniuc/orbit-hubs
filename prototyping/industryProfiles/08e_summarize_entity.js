// 08e_summarize_entity.js — Generate entity bio snippets
import pkg from 'pg';
const { Client } = pkg;
import dotenv from 'dotenv';

dotenv.config();

const client = new Client({
    host: process.env.DB_HOST,
    port: process.env.DB_PORT,
    database: process.env.DB_NAME,
    user: process.env.DB_USER,
    password: process.env.DB_PASSWORD,
});

async function getLlmSummary(name, docs) {
    const context = docs.map(d => `Source: ${d.source_label}\nContent: ${d.text}`).join('\n\n---\n\n');
    const prompt = `Du bist ein hilfreicher Assistent. Erstelle eine kurze, professionelle Zusammenfassung (2-3 Sätze, Deutsch) für die Person/Firma "${name}" basierend auf den folgenden Dokumenten. Diese Zusammenfassung wird als Vorschau in einer Suche angezeigt.
    
Dokumente:
${context}

Antworte NUR mit dem Text der Zusammenfassung.`;

    const res = await fetch("http://localhost:11434/api/generate", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
            model: process.env.RATIONALE_MODEL,
            prompt: prompt,
            stream: false,
            keep_alive: "30m"
        }),
    });
    if (!res.ok) throw new Error(`Summary generation failed: ${res.status}`);
    const data = await res.json();
    return data.response.trim();
}

export async function summarizeEntity(client, entityId) {
    console.log(`[Summary] Summarizing ${entityId}...`);
    
    const { rows: entities } = await client.query('SELECT name FROM entities WHERE id = $1', [entityId]);
    if (entities.length === 0) return;
    
    const { rows: docs } = await client.query('SELECT source_label, text FROM documents WHERE entity_id = $1 LIMIT 5', [entityId]);
    if (docs.length === 0) return;

    const summary = await getLlmSummary(entities[0].name, docs);
    
    await client.query('UPDATE entities SET summary = $1 WHERE id = $2', [summary, entityId]);
    console.log(`  - Summary: ${summary.substring(0, 50)}...`);
}

// CLI usage
if (process.argv[1].endsWith('08e_summarize_entity.js')) {
    const entityId = process.argv[2];
    if (!entityId) {
        console.error('Usage: node 08e_summarize_entity.js <entityId>');
        process.exit(1);
    }
    
    await client.connect();
    try {
        await summarizeEntity(client, entityId);
        console.log('Done.');
    } catch (err) {
        console.error(err);
    } finally {
        await client.end();
    }
}
