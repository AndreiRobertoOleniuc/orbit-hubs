// 08a_ingest_github.js — Ingest GitHub bio and top repos
import pkg from 'pg';
const { Client } = pkg;
import dotenv from 'dotenv';
import { getEmbedding, truncate, chunkText } from './08_utils.js';

dotenv.config();

const client = new Client({
    host: process.env.DB_HOST,
    port: process.env.DB_PORT,
    database: process.env.DB_NAME,
    user: process.env.DB_USER,
    password: process.env.DB_PASSWORD,
});

const GITHUB_TOKEN = process.env.GITHUB_PAT;

async function fetchGitHub(path) {
    const res = await fetch(`https://api.github.com${path}`, {
        headers: {
            'Authorization': `token ${GITHUB_TOKEN}`,
            'Accept': 'application/vnd.github.v3+json',
            'User-Agent': 'BadenConnect-Prototype/0.1'
        }
    });
    if (!res.ok) {
        if (res.status === 404) return null;
        throw new Error(`GitHub API error: ${res.status} ${res.statusText} for ${path}`);
    }
    return res.json();
}

async function fetchReadme(owner, repo) {
    const data = await fetchGitHub(`/repos/${owner}/${repo}/readme`);
    if (!data || !data.content) return null;
    return Buffer.from(data.content, 'base64').toString('utf8');
}

export async function ingestGitHub(client, entityId, username) {
    console.log(`[GitHub] Ingesting ${username} for ${entityId}...`);
    
    // 1. User Bio
    const user = await fetchGitHub(`/users/${username}`);
    if (user) {
        const bioText = `${user.name || username}. ${user.bio || ''}. Location: ${user.location || 'Unknown'}. Followers: ${user.followers}.`;
        const embedding = await getEmbedding(bioText);
        
        await client.query(`
            INSERT INTO documents (id, entity_id, source_type, source_url, source_label, text, embedding, meta)
            VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
            ON CONFLICT (id) DO UPDATE SET text = EXCLUDED.text, embedding = EXCLUDED.embedding, fetched_at = NOW()
        `, [
            `gh-${username}-bio`,
            entityId,
            'github_bio',
            `https://github.com/${username}`,
            'GitHub Profile Bio',
            bioText,
            JSON.stringify(embedding),
            JSON.stringify({ followers: user.followers, location: user.location })
        ]);
    }

    // 2. Top Repos
    let repos = await fetchGitHub(`/users/${username}/repos?sort=stars&direction=desc&per_page=10`);
    if (repos && Array.isArray(repos)) {
        for (const repo of repos) {
            if (repo.fork) continue; // skip forks
            
            console.log(`  - Ingesting repo: ${repo.name}`);
            const readme = await fetchReadme(username, repo.name);
            const baseText = `${repo.name} — ${repo.description || 'No description'}. \nPrimary language: ${repo.language || 'Unknown'}. Topics: ${(repo.topics || []).join(', ')}.`;
            
            const readmeChunks = chunkText(readme || '');
            for (let i = 0; i < Math.min(readmeChunks.length, 3); i++) { // Limit to 3 chunks per README
                const repoText = `${baseText} \n\nREADME excerpt (${i+1}):\n${readmeChunks[i]}`;
                const embedding = await getEmbedding(repoText);
                
                await client.query(`
                    INSERT INTO documents (id, entity_id, source_type, source_url, source_label, text, embedding, meta)
                    VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
                    ON CONFLICT (id) DO UPDATE SET text = EXCLUDED.text, embedding = EXCLUDED.embedding, fetched_at = NOW()
                `, [
                    `gh-${username}-repo-${repo.name}-chunk-${i}`,
                    entityId,
                    'github_repo',
                    repo.html_url,
                    `GitHub repo · ${repo.name}${i > 0 ? ` (${i+1})` : ''}`,
                    repoText,
                    JSON.stringify(embedding),
                    JSON.stringify({ stars: repo.stargazers_count, language: repo.language, topics: repo.topics, chunk: i })
                ]);
            }
        }
    }
}

// CLI usage
if (process.argv[1].endsWith('08a_ingest_github.js')) {
    const username = process.argv[2];
    const entityId = process.argv[3];
    if (!username || !entityId) {
        console.error('Usage: node 08a_ingest_github.js <username> <entityId>');
        process.exit(1);
    }
    
    await client.connect();
    try {
        await ingestGitHub(client, entityId, username);
        console.log('Done.');
    } catch (err) {
        console.error(err);
    } finally {
        await client.end();
    }
}
