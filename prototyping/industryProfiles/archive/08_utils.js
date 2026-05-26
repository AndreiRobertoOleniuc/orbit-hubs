import dotenv from 'dotenv';
dotenv.config();

export async function getEmbedding(text) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 60000);

    try {
        const start = Date.now();
        console.log(`    [${new Date().toLocaleTimeString()}] [Embedding] Calling localhost:11434...`);
        const res = await fetch("http://localhost:11434/api/embeddings", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                model: process.env.EMBEDDING_MODEL,
                prompt: `passage: ${text}`,
                keep_alive: "30m",
            }),
            signal: controller.signal
        });
        clearTimeout(timeout);
        if (!res.ok) throw new Error(`embedding failed: ${res.status}`);
        const data = await res.json();
        console.log(`    [${new Date().toLocaleTimeString()}] [Embedding] Success in ${Date.now() - start}ms`);
        return data.embedding;
    } catch (err) {
        clearTimeout(timeout);
        console.error(`    [${new Date().toLocaleTimeString()}] [Embedding] Error:`, err.message);
        throw err;
    }
}

export async function getRationale(prompt) {
    const start = Date.now();
    console.log(`    [${new Date().toLocaleTimeString()}] [Rationale] Calling localhost:11434...`);
    const res = await fetch("http://localhost:11434/api/generate", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
            model: process.env.RATIONALE_MODEL,
            prompt: prompt,
            stream: false,
            format: "json",
            keep_alive: "30m"
        }),
    });
    if (!res.ok) throw new Error(`rationale failed: ${res.status}`);
    const data = await res.json();
    console.log(`    [${new Date().toLocaleTimeString()}] [Rationale] Success in ${Date.now() - start}ms`);
    return JSON.parse(data.response);
}

export function truncate(text, maxChars = 1000) {
    if (text.length <= maxChars) return text;
    return text.substring(0, maxChars);
}

export function chunkText(text, chunkSize = 1000, overlap = 100) {
    if (text.length <= chunkSize) return [text];
    
    const chunks = [];
    let start = 0;
    
    while (start < text.length) {
        const end = Math.min(start + chunkSize, text.length);
        chunks.push(text.substring(start, end));
        start += (chunkSize - overlap);
        
        // Prevent infinite loops or tiny final chunks
        if (start >= text.length - overlap) break;
    }
    
    return chunks;
}
