#!/usr/bin/env node
/**
 * Zefix Zweck harvester — fetches company data with purpose descriptions
 * from the Swiss LINDAS SPARQL endpoint.
 *
 * Usage:
 *   node harvest.js aargau      # ~50k companies in canton Aargau
 *   node harvest.js all         # ~770k all Switzerland (paginated, ~45min)
 *
 * Output: zefix_<scope>.csv in the current directory.
 *
 * Resilience:
 *   - 180s per-request timeout via AbortController
 *   - 5 retries with exponential backoff on network/HTTP errors
 *   - Keyset pagination (no deep-OFFSET slowdown)
 *   - Resume from last written row if zefix_all.csv exists
 *
 * Requires: Node.js 18+ (for built-in fetch). No npm install needed.
 */

import { writeFile, appendFile, readFile, stat } from 'node:fs/promises';

const ENDPOINT = 'https://lindas.admin.ch/query';
const PAGE_SIZE = 10000;
const DELAY_BETWEEN_PAGES_MS = 1000; // be polite to LINDAS
const REQUEST_TIMEOUT_MS = 180_000;  // 3 min per page
const MAX_RETRIES = 5;
const BACKOFF_BASE_MS = 2000;        // 2s, 4s, 8s, 16s, 32s

const sleep = ms => new Promise(r => setTimeout(r, ms));

const PREFIXES = `
PREFIX schema: <http://schema.org/>
PREFIX admin:  <https://schema.ld.admin.ch/>
`.trim();

const CORE_PATTERN = `
?company a admin:ZefixOrganisation ;
         schema:legalName   ?legalName ;
         schema:description ?desc ;
         admin:municipality ?muniId ;
         schema:identifier  ?uidIri ;
         schema:address     ?addr .
FILTER(CONTAINS(STR(?uidIri), "/UID/"))
BIND(STRAFTER(STR(?uidIri), "/UID/") AS ?uid)
OPTIONAL { ?addr schema:streetAddress   ?street }
OPTIONAL { ?addr schema:postalCode      ?postalCode }
OPTIONAL { ?addr schema:addressLocality ?locality }
OPTIONAL { ?addr schema:addressRegion   ?canton }
`.trim();

/**
 * Build SPARQL query. For 'all' scope we use keyset pagination on ?company
 * (cursor = last company URI) instead of OFFSET, which avoids the linear
 * slowdown the server would otherwise show as we walk deeper into the result.
 */
function buildQuery({ scope, limit, cursor }) {
    const filters = [];
    if (scope === 'aargau') {
        // schema:addressRegion holds the canton abbreviation as a literal
        filters.push(`FILTER(STR(?canton) = "AG")`);
    }
    if (scope === 'all' && cursor) {
        // Lexicographic > on the URI string. Stable because we ORDER BY ?company.
        filters.push(`FILTER(STR(?company) > "${cursor}")`);
    }

    const pagination = scope === 'all'
        ? `ORDER BY ?company\nLIMIT ${limit}`
        : ''; // Aargau is small enough to fetch in one go

    return `${PREFIXES}
SELECT ?company ?uid ?legalName ?desc
       ?street ?postalCode ?locality ?canton
       ?muniId ?muniName
WHERE {
  GRAPH <https://lindas.admin.ch/foj/zefix> {
${CORE_PATTERN}
${filters.join('\n')}
  }
  OPTIONAL { ?muniId schema:name ?muniName }
}
${pagination}`.trim();
}

/**
 * Single request with timeout. Throws on HTTP !ok or network failure.
 */
async function fetchOnce(query) {
    const url = `${ENDPOINT}?query=${encodeURIComponent(query)}`;
    const ctrl = new AbortController();
    const timer = setTimeout(() => ctrl.abort(), REQUEST_TIMEOUT_MS);
    const t0 = Date.now();
    try {
        const res = await fetch(url, {
            headers: {
                'Accept': 'application/sparql-results+json',
                'User-Agent': 'BadenConnect-Harvester/0.2',
            },
            signal: ctrl.signal,
        });
        if (!res.ok) {
            const body = await res.text();
            throw new Error(`HTTP ${res.status}: ${body.slice(0, 300)}`);
        }
        const json = await res.json();
        return { rows: json.results.bindings, ms: Date.now() - t0 };
    } finally {
        clearTimeout(timer);
    }
}

/**
 * Wrap fetchOnce with exponential backoff.
 */
async function fetchPage(query) {
    let lastErr;
    for (let attempt = 1; attempt <= MAX_RETRIES; attempt++) {
        try {
            return await fetchOnce(query);
        } catch (err) {
            lastErr = err;
            const cause = err.cause?.code || err.name || 'unknown';
            if (attempt === MAX_RETRIES) break;
            const wait = BACKOFF_BASE_MS * 2 ** (attempt - 1);
            console.warn(`  ⚠ attempt ${attempt}/${MAX_RETRIES} failed (${cause}: ${err.message.slice(0, 120)}), retrying in ${wait}ms`);
            await sleep(wait);
        }
    }
    throw lastErr;
}

function csvCell(v) {
    if (v == null) return '';
    const s = String(v);
    if (/[",\n\r]/.test(s)) return `"${s.replaceAll('"', '""')}"`;
    return s;
}

const v = (b, k) => b[k]?.value ?? '';

function rowToCsv(b) {
    return [
        v(b, 'company'),
        v(b, 'uid'),
        v(b, 'legalName'),
        v(b, 'desc'),
        v(b, 'street'),
        v(b, 'postalCode'),
        v(b, 'locality'),
        v(b, 'canton'),
        v(b, 'muniId'),
        v(b, 'muniName'),
    ].map(csvCell).join(',');
}

const HEADER = 'company_uri,uid,legal_name,description,'
    + 'street,postal_code,locality,canton,'
    + 'muni_id,muni_name\n';

async function harvestAargau() {
    const out = 'zefix_aargau.csv';
    console.log(`Fetching canton Aargau (single query)...`);
    const query = buildQuery({ scope: 'aargau' });
    const { rows, ms } = await fetchPage(query);
    const lines = rows.map(rowToCsv).join('\n');
    await writeFile(out, HEADER + lines + '\n', 'utf-8');
    console.log(`✓ ${rows.length} rows in ${ms}ms → ${out}`);
}

async function readLastLine(path) {
    const { size } = await stat(path);
    if (size === 0) return null;
    const tailSize = Math.min(size, 65536);
    const fh = await (await import('node:fs/promises')).open(path, 'r');
    try {
        const buf = Buffer.alloc(tailSize);
        await fh.read(buf, 0, tailSize, size - tailSize);
        const text = buf.toString('utf-8');
        const lines = text.split('\n').filter(l => l.length > 0);
        return lines.length ? lines[lines.length - 1] : null;
    } finally {
        await fh.close();
    }
}

function firstCellOf(line) {
    if (!line) return null;
    if (line.startsWith('"')) {
        const end = line.indexOf('"', 1);
        return end > 0 ? line.slice(1, end) : null;
    }
    const comma = line.indexOf(',');
    return comma > 0 ? line.slice(0, comma) : line;
}

async function resumeCursor(out) {
    try {
        await stat(out);
    } catch {
        return null;
    }
    const last = await readLastLine(out);
    const cell = firstCellOf(last);
    if (!cell || cell === 'company_uri') return null;
    return cell;
}

async function harvestAll() {
    const out = 'zefix_all.csv';
    let cursor = await resumeCursor(out);
    let total = 0;

    if (cursor) {
        const existing = await readFile(out, 'utf-8');
        total = existing.split('\n').filter(l => l.length > 0).length - 1;
        console.log(`↻ Resuming from ${total} rows already written, cursor=${cursor}`);
    } else {
        await writeFile(out, HEADER, 'utf-8');
        console.log(`▶ Starting fresh harvest`);
    }

    while (true) {
        const query = buildQuery({ scope: 'all', limit: PAGE_SIZE, cursor });
        const { rows, ms } = await fetchPage(query);
        if (rows.length === 0) {
            console.log(`Done, total ${total}`);
            break;
        }
        const lines = rows.map(rowToCsv).join('\n') + '\n';
        await appendFile(out, lines, 'utf-8');
        total += rows.length;

        const lastRow = rows[rows.length - 1];
        cursor = lastRow.company.value;

        console.log(`  +${rows.length} in ${ms}ms (total ${total}, cursor ${cursor.slice(-30)})`);

        if (rows.length < PAGE_SIZE) {
            console.log(`Last page (got ${rows.length} < ${PAGE_SIZE})`);
            break;
        }
        await sleep(DELAY_BETWEEN_PAGES_MS);
    }
    console.log(`✓ ${total} rows → ${out}`);
}

const scope = process.argv[2];
if (scope === 'aargau') await harvestAargau();
else if (scope === 'all') await harvestAll();
else {
    console.error('Usage: node harvest.js <aargau|all>');
    process.exit(1);
}