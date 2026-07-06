# Orbit Ingestion & Search — Complete Code Walkthrough

Every class, every method, every design decision. Organized by data flow — from REST request to search result.

**Last updated:** 2026-07-05 (evening) — reflects all 7 adapters done, chunk quality fixes, search validated at 96%.

---

## Architecture Overview

```
                    INGESTION FLOW
                    ══════════════

POST /api/v1/ingestion                    IngestionController
  {"sourceType":"github",                      │
   "context":{"github_login":"tiangolo"}}      ▼
                                    SourceAdapterRegistry.getAdapter("github")
                                              │
                                              ▼
                                    GithubAdapter.discoverSources()
                                    → fetches profile + pinned repos + search repos
                                    → returns List<SourceRef> (one per repo, up to 15)
                                              │
                                              ▼
                                    IngestionOrchestrator.ingestMulti()
                                    → first repo sequential (creates entity)
                                    → remaining repos parallel (5 threads)
                                              │
                                    per repo:  ▼
                                    IngestionOrchestrator.ingest()  [@Transactional]
                                    │
                                    ├─ 1. FETCH:        adapter.fetch(ref) → RawContent
                                    ├─ 2. EXTRACT:      adapter.extractHighSignalCore(raw) → coreText
                                    │                   (Gemini LLM summary or structured text)
                                    ├─ 3. DEDUP:        sources.findBySourceUrl() + hash check
                                    ├─ 4. RESOLVE:      findEntityByAnyId(extIds) → reuse or create
                                    ├─ 5. UPSERT:       source row (delete old, insert new)
                                    ├─ 6. CHUNK:        ChunkingService.recursiveSplit(coreText, 400, 60)
                                    │                   → merge tiny chunks (< 30 tokens) with adjacent
                                    └─ 7. EMBED+PERSIST: embed("passage: "+text) → vector → save Chunk

POST /api/v1/ingestion/linkedin-pdf        IngestionController
  multipart/form-data file=@Profile.pdf        │
                                               ▼
                                    Save to temp file → SourceRef with content-hash URL
                                    → LinkedInPdfAdapter → PDFBox parse → chunks
                                    → Delete temp file in finally block


                    SEARCH FLOW
                    ═══════════

POST /api/v1/search                    SearchController
  {"query":"Python web framework"}         │
                                          ▼
                                    SearchService.search()
                                    │
                                    ├─ 1. EMBED QUERY:  embeddingService.embed("query: "+text, QUERY)
                                    ├─ 2. HYBRID SEARCH: SQL function hybrid_search()
                                    │      ├─ FTS leg:    ts_rank_cd(fts, websearch_to_tsquery('english', q))
                                    │      ├─ Semantic:   embedding <#> query_embedding (pgvector)
                                    │      └─ RRF FUSION:  weight/(rrf_k + rank), max(score) rollup
                                    └─ 3. EVIDENCE:      per entity, fetch top-3 chunks (deduped by source)
```

---

## 0. Package Structure

The codebase separates **DTOs** (pure data records) from **services/interfaces/exceptions** in parent packages:

```
com.orbit.app.ingestion/
  ├── SourceAdapter.java              ← interface
  ├── SourceAdapterRegistry.java      ← registry
  ├── ChunkingService.java            ← chunking + merge tiny chunks
  ├── IngestionOrchestrator.java      ← core pipeline
  ├── IngestionException.java         ← exception (NOT a DTO)
  ├── dto/
  │   ├── SourceRef.java
  │   ├── RawContent.java
  │   ├── ExternalId.java
  │   ├── EntityProfile.java
  │   ├── ChunkSegment.java
  │   └── IngestionResult.java
  ├── github/GithubAdapter.java
  ├── openalex/OpenAlexAdapter.java
  ├── linkedin/
  │   ├── LinkedInPdfAdapter.java
  │   ├── LinkedInPdfParser.java
  │   └── dto/ParsedLinkedInProfile.java
  ├── zefix/ZefixAdapter.java
  ├── manual/ManualTextAdapter.java
  └── website/
      ├── WebsiteContentExtractor.java        ← shared utility
      ├── personal/PersonalWebsiteAdapter.java
      └── company/CompanyWebsiteAdapter.java

com.orbit.app.search/
  ├── SearchService.java
  ├── SearchController.java
  └── dto/SearchResult.java

com.orbit.app.embedding/
  ├── EmbeddingService.java
  ├── EmbeddingKind.java
  ├── EmbeddingProperties.java
  ├── EmbeddingConfig.java
  └── OllamaEmbeddingService.java

com.orbit.app.llm/
  └── LlmSummaryService.java       ← 4 methods now (was 1)

com.orbit.domain.entity/           ← DiscoveryEntity, EntityIdentifier, Source, Chunk
com.orbit.domain.repository/       ← JPA repositories
com.orbit.controllers/             ← IngestionController
com.orbit.security/                ← ClerkSecurityConfig
```

---

## 1. REST Entry Points

### IngestionController.java
**Role:** REST endpoints for ingestion from any source type.

**Two endpoints:**

```
POST /api/v1/ingestion
  Body: {"sourceType": "github", "context": {"github_login": "tiangolo"}}
  Response: {"sources": 15, "ingested": 15, "errors": 0, "totalChunks": 15, ...}

POST /api/v1/ingestion/linkedin-pdf
  Content-Type: multipart/form-data
  file=@Profile.pdf
  Response: same format
```

**Key methods:**

- `ingest(IngestionRequest)` — builds a `SourceRef`, calls `orchestrator.ingestMulti()`, aggregates results
- `uploadLinkedInPdf(MultipartFile)` — saves PDF to temp file, builds `SourceRef` with `linkedin:pdf:{sha256}` URL (content-based idempotency), passes file_path in context, deletes temp file in finally block
- `buildUrl(IngestionRequest)` — constructs the source URL per source type:
  - `github` → `https://github.com/{github_login}`
  - `orcid_pub` → `https://orcid.org/{orcid}` or `https://openalex.org/authors/{openalex_author_id}`
  - `linkedin_pdf` → `linkedin:pdf:{file_path}` (or content hash for upload)
  - `zefix` → `zefix:{uid}` or `zefix:name:{company_name}`
  - `manual_text` → `manual:text:{sha256(text)}` (content-based idempotency)
  - `personal_site` → `personal_site:{domain}` (normalized domain)
  - `company_site` → `company_site:{domain}` (normalized domain)
- `provenanceFor(sourceType)` — maps source types to provenance/consent pairs:
  - `github`, `orcid_pub`, `zefix` → `harvested` / `public_data`
  - `linkedin_pdf` → `user_supplied` / `user_consent`
  - `manual_text` → `user_supplied` / `self_declared`
  - `personal_site`, `company_site` → `harvested` / `public_data`

### SearchController.java
```
POST /api/v1/search
  Body: {"query": "Python web framework", "matchCount": 5}
  Response: [{entityId, displayName, headline, orgName, entityType, score, evidence: [...]}]
```

---

## 2. Value Objects (data flowing through the pipeline)

All `record` types — immutable, no logic, in `dto` subpackages.

### SourceRef.java — "what to ingest"
```java
record SourceRef(
    String url,                    // unique idempotency key
    String sourceType,             // "github" | "orcid_pub" | "linkedin_pdf" | "zefix" | "manual_text" | "personal_site" | "company_site"
    String provenance,             // "harvested" | "user_supplied"
    String consentBasis,           // "public_data" | "user_consent" | "self_declared"
    Map<String, String> context    // source-specific params
)
```

### RawContent.java — "what was fetched"
```java
record RawContent(String text, Map<String, Object> metadata, Instant fetchedAt)
```

### ExternalId.java — "who is this entity"
```java
record ExternalId(String idType, String idValue)
// Types: github_login, orcid, openalex_author_id, doi, linkedin_url, zefix_uid, domain
```

### EntityProfile.java — "entity creation data"
```java
record EntityProfile(String entityType, String displayName, String headline,
                      String orgName, Double latitude, Double longitude, Map<String, Object> metadata)
```

### ChunkSegment.java — "a piece of text ready to embed"
```java
record ChunkSegment(String text, String section, int tokenCount)
```

### IngestionResult.java — "what happened"
```java
record IngestionResult(Long entityId, Long sourceId, int chunkCount, Status status)
// Status: INGESTED | SKIPPED | ERROR
```

---

## 3. Source Adapter Pattern

### SourceAdapter.java — the interface
```java
public interface SourceAdapter {
    String sourceType();
    RawContent fetch(SourceRef ref);
    String extractHighSignalCore(RawContent raw);
    String buildLabel(RawContent raw);
    List<ExternalId> extractExternalIds(RawContent raw);
    EntityProfile deriveProfile(RawContent raw);

    default List<SourceRef> discoverSources(SourceRef ref) {  // multi-source discovery
        return List.of(ref);                                  // default: one source
    }
}
```

**Adding a new source = implement this interface + `@Component`.** The registry auto-discovers it. Zero changes to the orchestrator.

### SourceAdapterRegistry.java — adapter lookup
Spring injects all `SourceAdapter` beans → maps them by `sourceType()` string.

---

## 4. Adapters (7/7 done)

### 4.1 GithubAdapter.java  (`com.orbit.app.ingestion.github`)

**sourceType() = "github"** | **provenance: harvested / public_data**

**discoverSources() — multi-source:**
1. `fetchUser(username)` — GET `/users/{username}` → profile
2. `fetchPinnedRepos(username)` — GraphQL `user.pinnedItems(first: 6)` → up to 6 pinned repos
3. `searchReposByStars(username)` — GET `/search/repositories?q=user:{username}+fork:false&sort=stars` → top repos
4. Merge: pinned first, then personal (dedup), up to 15 total
5. Return one `SourceRef` per repo with cached metadata

**fetch() — get README for one repo:**
- `fetchRepo(owner, repo)` → repo metadata
- `fetchReadme(owner, repo)` → README text

**extractHighSignalCore() — LLM with fallback chain:**
1. If README exists → `llmSummaryService.summarizeGithubRepo()` → clean summary
2. If no README → `llmSummaryService.describeGithubRepoFromMetadata()` → 2-3 sentences from metadata
3. If both fail → `buildMechanicalCoreText()` → "Repo: X\nDescription: Y" (last resort)

**extractExternalIds() → `("github_login", username)`**

**Config:** `orbit.github.token` (GitHub PAT for GraphQL + rate limits)

---

### 4.2 OpenAlexAdapter.java  (`com.orbit.app.ingestion.openalex`)

**sourceType() = "orcid_pub"** | **provenance: harvested / public_data**

**discoverSources() — multi-source:**
1. `resolveAuthorId(ref)` — ORCID primary (checks `orcid` context key), OpenAlex ID fallback
2. `fetchAuthor(authorId)` — ORCID path: `GET /authors/https://orcid.org/{orcid}`, OpenAlex ID path: `GET /authors/{id}`
3. `fetchWorks(openalexId)` — `GET /works?filter=author.id:{id}&sort=cited_by_count:desc&per_page=25`
4. For each work: reconstruct abstract from inverted index, return SourceRef with cached data

**extractHighSignalCore() — with empty-abstract enrichment:**
```
## {primary_topic}
{title}
{abstract}                          ← if available
OR
Published in {year}. Cited by {count}. Research area: {topic}.  ← if no abstract
```
The enrichment prevents tiny chunks (was: just `## topic\ntitle` = 15 tokens; now: ~30 tokens with metadata context).

**stripHtml() — removes `<strong>`, `<em>` tags from reconstructed abstracts**

**reconstructAbstract() — reverse the inverted index:**
```java
// OpenAlex stores: {"word": [0, 5], "another": [1], ...}
// We reconstruct: word array → join with spaces → plain text
```

**extractExternalIds() → ORCID (primary) + openalex_author_id (secondary) + doi (per-work)**

**Key technical notes:**
- `URI.create()` for fetchJson — prevents RestClient from URL-encoding `://` in ORCID paths
- Jackson 3.x: `properties()` not `fields()` (fields() doesn't exist in Jackson 3.x)
- `sort=cited_by_count:desc` — OpenAlex uses `:desc` suffix, not `-` prefix

**Config:** `orbit.openalex.mailto` (polite pool, no API key needed)

---

### 4.3 LinkedInPdfAdapter.java  (`com.orbit.app.ingestion.linkedin`)

**sourceType() = "linkedin_pdf"** | **provenance: user_supplied / user_consent**

**fetch() — parse PDF:**
- Reads PDF file from `file_path` in context (temp file saved by controller)
- `LinkedInPdfParser.parse(pdfText)` → `ParsedLinkedInProfile` (name, sections, skills, experience, education)
- Returns `RawContent` with structured profile data

**extractHighSignalCore() — build structured text:**
```
## {name}
## Summary
{summary text}

## Top Skills
- {skill1}, {skill2}, ...

## Experience
{company1} — {title1}
{description1}

## Education
{institution1} — {degree1}
```
Each `##` section becomes a chunk section label.

**extractExternalIds() → `("linkedin_url", profileUrl)`** (if URL found in PDF)

**LinkedInPdfParser.java — multi-language PDF text parser:**
- **PDFBox 3.0.5** for PDF text extraction
- **Non-breaking space fix:** `text.replace('\u00A0', ' ')` before stripping — Java's `trim()`/`strip()` don't remove NBSP (U+00A0)
- **Form feed handling:** `\f` page separators cleaned
- **Multi-language section headers:** Dictionary maps both EN and DE headers to canonical keys:
  - Contact/Kontakt, Top Skills/Top-Kenntnisse, Summary/Zusammenfassung
  - Experience/Berufserfahrung, Education/Ausbildung, Certifications/Zertifikate
- **Name extraction:** Walk backwards from first main section header. `looksLikeName()` heuristic: 2-5 words, excludes job-prepositions, punctuation, email/URL/phone patterns
- **Line wrapping:** Join URLs ending with `-` with next line; join emails ending with `.` if next line looks like TLD; merge standalone `(LinkedIn)` tags

**ParsedLinkedInProfile.java (dto):**
```java
record ParsedLinkedInProfile(String name, String headline, String location,
    String url, Map<String, String> sections, List<String> skills)
```

**Upload flow:**
1. Controller saves `MultipartFile` bytes to temp file
2. Source URL = `linkedin:pdf:{sha256(bytes)}` — content-based idempotency (re-uploading same PDF = dedup skip)
3. Passes `file_path` in context to adapter
4. Controller deletes temp file in `finally` block

---

### 4.4 ZefixAdapter.java  (`com.orbit.app.ingestion.zefix`)

**sourceType() = "zefix"** | **provenance: harvested / public_data**

**fetch() — Swiss Commercial Register API:**
1. If `zefix_uid` in context → `GET /api/v1/company/uid/{uid}` (direct lookup)
2. If `company_name` in context → `POST /api/v1/company/search` with `{"name": "...", "activeOnly": true}` → take first result → fetch full details by UID
3. Parse `CompanyFull` JSON → name, uid, purpose (Zweck), legalForm, address, canton, status, capital

**extractHighSignalCore() — Zweck with Gemini cleanup:**
```
## {company_name}
{cleaned_purpose}
```
If Zweck is empty/short, fallback: `{legalForm} based in {legalSeat}, {canton}.`

**cleanZweck() via Gemini:** Swiss company purposes contain standard legal boilerplate about branches, subsidiaries, real estate, financing. Gemini strips it, keeping only substantive business description. Example: 699 chars → 192 chars.

**extractExternalIds() → `("zefix_uid", normalizedUid)`**
UID normalization: `CHE414461970` → `CHE-414.461.970` (canonical format with dashes and dots)

**deriveProfile() → organization profile with canton, legal form, address metadata**

**Config:**
```yaml
orbit.zefix:
  base-url: https://www.zefix.admin.ch/ZefixPublicREST
  user: ${ZEFIX_USER}    # env var
  key: ${ZEFIX_KEY}      # env var
```
**Auth:** HTTP Basic Auth with `ZEFIX_USER` and `ZEFIX_KEY` environment variables (set in `~/.zprofile`).

---

### 4.5 ManualTextAdapter.java  (`com.orbit.app.ingestion.manual`)

**sourceType() = "manual_text"** | **provenance: user_supplied / self_declared**

**The simplest adapter — no fetching, no API calls, text IS the content.**

**fetch() — text from context:**
```java
String text = ref.context().get("text");
return new RawContent(text, metadata, Instant.now());
```

**extractHighSignalCore() — Gemini expansion for short text:**
- If text < 200 chars → `llmSummaryService.enrichManualText(text, name)` → Gemini expands conservatively
- Expansion stays faithful: elaborates on what user wrote, doesn't invent skills/companies/experience
- Keeps original language (DE→DE, EN→EN)
- Example: "React Dev" (9 chars) → 371 chars. "Datenanalyst" (12 chars) → 320 chars in German.
- Wraps in `## Eigene Beschreibung\n{expanded_text}`

**extractExternalIds() → empty list** (no external IDs for self-declared content)

**buildLabel() → "Eigene Beschreibung"**

**deriveProfile() → person (default), name from context, headline from first sentence**

---

### 4.6 PersonalWebsiteAdapter.java  (`com.orbit.app.ingestion.website.personal`)

**sourceType() = "personal_site"** | **provenance: harvested / public_data**

**fetch() — crawl personal website:**
1. `WebsiteContentExtractor.crawl(url, maxPages=5, domain)` → priority-first BFS
2. Fetch homepage, discover internal links, score by URL pattern, fetch top-5 pages
3. For each page: 3-layer boilerplate removal → structured text with ## headings
4. Returns `RawContent` with all page texts concatenated, domain, crawled URLs, site name

**extractHighSignalCore():**
```
=== PERSONAL WEBSITE: {domain} ===

--- {page1_title} ---
{page1_text}

--- {page2_title} ---
{page2_text}
...
```

**extractExternalIds() → `("domain", normalizedDomain)`**
Domain normalized: `https://www.johndoe.dev/about` → `johndoe.dev`

**deriveProfile() → person, displayName from page title or domain**

---

### 4.7 CompanyWebsiteAdapter.java  (`com.orbit.app.ingestion.website.company`)

**sourceType() = "company_site"** | **provenance: harvested / public_data**

**fetch() — multi-page priority crawl:**
1. `WebsiteContentExtractor.crawl(url, maxPages=10, domain)` → priority-first BFS
2. URL priority scoring (higher = more valuable):
   - `/about`, `/ueber-uns`, `/a-propos` → 100
   - `/team`, `/mitarbeiter` → 95
   - `/services`, `/leistungen` → 90
   - `/products`, `/produkte` → 85
   - `/portfolio`, `/projekte` → 80
   - `/impressum`, `/imprint` → 10 (low signal but has CHE UID)
3. 8000 char content cap (after extraction, truncate to keep embedding manageable)
4. **CHE UID extraction:** Scan all page text for `CHE-NNN.NNN.NNN` pattern → cross-source entity resolution with Zefix

**extractHighSignalCore():**
```
=== COMPANY WEBSITE: {domain} ===

--- {page1_title} ---
{page1_text}
...
```

**extractExternalIds() → `("domain", domain)` + `("zefix_uid", uid)` if found in imprint**

**deriveProfile() → organization, displayName from og:site_name or page title**

---

### WebsiteContentExtractor.java — shared utility  (`com.orbit.app.ingestion.website`)

**Used by both PersonalWebsiteAdapter and CompanyWebsiteAdapter.**

**3-layer boilerplate removal:**

1. **Remove noise containers:** `script, style, noscript, iframe, svg, nav, header, footer, aside`, `[class*='navbar']`, `[class*='cookie']`, `[class*='banner']`, `[class*='footer']`, `[class*='social-share']`, `[class*='testimonial']`, `[class*='mobile-menu']`, form elements. (Narrowed from initial broader patterns to avoid false positives on Angular Material sites.)

2. **Find main content:** Priority: `<main>` / `<article>` / `[role='main']` → `[class*='content']` / `#content` / `#main` → largest div by text length → `body`

3. **Extract structured text:** Walk DOM, insert `## ` before headings, preserve paragraphs/lists/blockquotes/tables. This preserves section structure for ChunkingService.

**Fallback chain for extraction failures:**
1. Structured extraction (DOM walk with ## headings)
2. `doc.body().text()` (post-noise-removal — Jsoup flat text)
3. Raw body text captured BEFORE noise removal (for sites where noise removal is too aggressive)

**HTTP configuration:**
- `User-Agent: Orbit-Discovery/1.0 (mailto:contact@orbit.ch)`
- `Accept: text/html,...` + `Accept-Language: en-US,en;q=0.9,de;q=0.8,fr;q=0.7`
- `ignoreHttpErrors(true)` — parse body even for non-200 responses
- HTTP 510 accepted (some Swiss servers return this for Jsoup's HTTP/1.1)
- 10s timeout, 1s politeness delay between page fetches
- Skip non-HTML resources (PDF, images, etc.)
- Strip URL fragments (#section) for dedup

**Known limitations:**
- JS-rendered SPAs (React/Next.js with minimal static HTML) → may extract < 50 chars → fails
- Some servers return HTTP 510 for Jsoup's HTTP/1.1 → `ignoreHttpErrors(true)` handles most cases
- These show user errors in the UI (acceptable for Stage 1; headless browser is a future enhancement)

---

## 5. LLM Summarization (Google Gemini 2.5 Flash-Lite)

### LlmSummaryService.java  (`com.orbit.app.llm`, ~280 lines)

**4 methods now (was 1 originally):**

### 5.1 summarizeGithubRepo(repoName, description, language, topics, readme)
- 200-300 word summary from README
- 3 retries with 2s × attempt backoff
- Returns null on failure → adapter falls back

### 5.2 describeGithubRepoFromMetadata(repoName, description, language, topics)
- For repos with NO README
- 2-3 sentence description from metadata only
- Faithful — doesn't invent features not in the metadata
- Returns null on failure → adapter falls back to mechanical "Repo: X" format
- Example: repo with just a name + language → "This project is a partial parser for C statements..."

### 5.3 cleanZweck(purpose)
- Strips Swiss legal boilerplate from Zefix Zweck
- Keeps only substantive business description
- Example: 699 chars (with branches/subsidiaries/real estate clauses) → 192 chars

### 5.4 enrichManualText(text, name)
- Expands short manual text (< 200 chars) for better embedding quality
- Conservative: elaborates on what user wrote, doesn't invent skills/companies/experience
- Keeps original language (DE→DE, EN→EN)
- Example: "React Dev" (9 chars) → 371 chars

**Chat Model Configuration — auto-configured by Spring AI:**
- `spring-ai-starter-model-google-genai` auto-configures `GoogleGenAiChatModel` bean
- `LlmSummaryService` injects `ChatModel` interface → gets Gemini automatically
- Deleted: `ChatConfig.java`, `LlmProperties.java` (no longer needed)

**Performance:**
| Metric | Value |
|---|---|
| LLM call time | 1.2-7.2s per call |
| Retries | 0 (100% success rate on attempt 1) |
| Total for 15 repos | ~24s (sequential first + parallel remaining) |

---

## 6. Core Pipeline

### IngestionOrchestrator.java  (`com.orbit.app.ingestion`, ~280 lines)

**ingestMulti(SourceRef ref) — multi-source entry point:**
1. `adapter.discoverSources(ref)` → `List<SourceRef>`
2. If 1 source → `self.ingest()` directly
3. If >1: first sequential (creates entity), remaining parallel (5 threads via CompletableFuture)
4. Self-injection via `@Lazy` for `@Transactional` proxy in parallel calls

**ingest(SourceRef ref) — per-source pipeline [@Transactional]:**
```
1. FETCH:        adapter.fetch(ref) → RawContent
2. EXTRACT:      adapter.extractHighSignalCore(raw) → coreText
3. DEDUP:        sources.findBySourceUrl() + sha256 hash check → skip if unchanged
4. RESOLVE:      findEntityByAnyId(extIds) → reuse or create entity + identifiers
5. UPSERT:       delete old source, save new Source with contentHash
6. CHUNK:        chunkingService.recursiveSplit(coreText, 400, 60) → merge tiny + apply overlap
7. EMBED+PERSIST: embed("passage: "+text) → float[1024] → save Chunk
```

**Idempotency:** `source_url` unique. Content hash short-circuits if only whitespace changed.

---

## 7. Chunking

### ChunkingService.java  (`com.orbit.app.ingestion`, ~450 lines)

**recursiveSplit(text, maxTokens, overlapTokens) → List<ChunkSegment>:**
```
1. splitRecursive(text, maxTokens, depth=0)
   ├─ depth 0: splitBySections (## headings) → section label = heading text
   ├─ depth 1: splitByPattern(PARAGRAPH_BOUNDARY = \n\s*\n)
   ├─ depth 2: splitByPattern(SENTENCE_BOUNDARY = [.!?]\s+[A-Z])
   └─ depth 3: splitByWords (word boundaries)

2. mergeTinyChunks(raw, maxTokens)         ← NEW
   ├─ if chunk < 30 tokens AND has next chunk → merge with next (keep next's section label)
   ├─ if chunk < 30 tokens AND is last chunk → merge with previous
   └─ skip merge if merged size > maxTokens (better tiny than oversize)

3. applyOverlap(chunks, overlapTokens, maxTokens)
   └─ prepend last ~60 tokens of previous chunk to current chunk
```

**MIN_CHUNK_TOKENS = 30** — threshold for merging tiny chunks.

**Why merge?** OpenAlex papers without abstracts produced chunks of 14-17 tokens (just `## topic\ntitle`). Merging combines these with adjacent chunks for better embedding quality. Fixed 7 of 16 tiny OpenAlex chunks. Combined with metadata enrichment in OpenAlexAdapter, reduced tiny chunks from 16 → 1.

**Critical: preserve original whitespace.** All splitting uses `text.substring(start, end)`. `tokenize()` is used ONLY for counting, never for reconstructing text.

**Token counting:** `countTokens(text) = tokenize(text).size()` — approximates BERT WordPiece tokenization.

---

## 8. Embedding (Ollama — unchanged)

### EmbeddingService.java — interface
```java
public interface EmbeddingService {
    float[] embed(String text, EmbeddingKind kind);  // QUERY or PASSAGE
}
```

### OllamaEmbeddingService.java
```java
@Override
public float[] embed(String text, EmbeddingKind kind) {
    String prefixed = switch (kind) {
        case QUERY    → props.queryPrefix() + text;    // "query: "
        case PASSAGE  → props.passagePrefix() + text;  // "passage: "
    };
    float[] vector = embeddingModel.embed(prefixed);  // validates 1024 dims
    return vector;
}
```

**Why prefixes?** `multilingual-e5-large` is a contrastive model — "query: " and "passage: " prefixes are mandatory. Without them, embeddings are meaningless.

**Config:** `orbit.embedding.model: jeffh/intfloat-multilingual-e5-large:f16`, dimensions: 1024, Ollama base-url: `http://localhost:11434`

---

## 9. JPA Entities

### DiscoveryEntity.java → `entities`
| Field | Column | Notes |
|---|---|---|
| `id` | `id` | Auto-generated |
| `entityType` | `entity_type` | "person" or "organization" |
| `displayName` | `display_name` | Not null |
| `headline` | `headline` | Bio or affiliation |
| `orgName` | `org_name` | Company/university |
| `latitude/longitude` | `latitude/longitude` | Null in Stage 1 |
| `status` | `status` | Default "active" |
| `metadata` | `metadata` | JSONB (`@JdbcTypeCode(SqlTypes.JSON)`) |

### EntityIdentifier.java → `entity_identifiers`
| Field | Column | Notes |
|---|---|---|
| `entityId` | `entity_id` | FK |
| `idType` | `id_type` | github_login, orcid, openalex_author_id, doi, linkedin_url, zefix_uid, domain |
| `idValue` | `id_value` | The ID value |

**Unique constraint:** `(id_type, id_value)` — one identifier maps to one entity.

### Source.java → `sources`
| Field | Column | Notes |
|---|---|---|
| `entityId` | `entity_id` | FK |
| `sourceType` | `source_type` | github, orcid_pub, linkedin_pdf, zefix, manual_text, personal_site, company_site |
| `sourceUrl` | `source_url` | Unique — idempotency key |
| `label` | `label` | Citation chip text |
| `rawContent` | `raw_content` | Full text |
| `contentHash` | `content_hash` | SHA-256 of normalized core text |
| `provenance` | `provenance` | harvested, user_supplied |
| `consentBasis` | `consent_basis` | public_data, user_consent, self_declared |

### Chunk.java → `chunks`
| Field | Column | Notes |
|---|---|---|
| `sourceId` | `source_id` | FK |
| `entityId` | `entity_id` | Denormalized for scoped filtering |
| `chunkIndex` | `chunk_index` | Position within source |
| `content` | `content` | The chunk text |
| `section` | `section` | Context label (heading text) |
| `tokenCount` | `token_count` | Estimated BERT tokens |
| `embedding` | `embedding` | `vector(1024)` via pgvector |
| `fts` | `fts` | Generated tsvector (DB-generated, English config) |

---

## 10. Search

### SearchService.java  (`com.orbit.app.search`)

**search(queryText, matchCount) → List<SearchResult>:**
```
1. EMBED QUERY:  embeddingService.embed("query: " + queryText, QUERY)
2. HYBRID SEARCH: SELECT entity_id, score FROM hybrid_search(query, embedding, matchCount, ...)
3. EVIDENCE: per entity, fetch top-3 chunks (deduped by source_id), sorted by similarity
   similarity = -distance (<#> returns negative inner product)
```

### SearchResult.java (dto)
```java
record SearchResult(
    Long entityId, String displayName, String headline, String orgName,
    String entityType, double score,
    List<EvidenceChunk> evidence
) {
    record EvidenceChunk(
        String content, String section, String sourceLabel, String sourceUrl,
        int tokenCount, double similarity
    ) {}
}
```

---

## 11. Database

### V2__discovery_engine_stage1.sql — 4 tables + indexes
### V3__hybrid_search_function.sql — FTS fix + hybrid_search() function

**hybrid_search function:**
```sql
CREATE FUNCTION hybrid_search(
  query_text text, query_embedding vector(1024), match_count int DEFAULT 10,
  full_text_weight double precision DEFAULT 1.0,
  semantic_weight double precision DEFAULT 1.0,
  rrf_k int DEFAULT 50
) RETURNS TABLE(entity_id bigint, score double precision)
```

- FTS leg: `ts_rank_cd(fts, websearch_to_tsquery('english', q))`
- Semantic leg: `embedding <#> query_embedding` (negative inner product)
- RRF fusion: `weight/(rrf_k + rank)` per leg, `max(score)` per entity
- `rrf_k=50`: rank 1 → 1/51 ≈ 0.0196, rank 2 → 1/52 ≈ 0.0192

**Key:** `double precision` (not `float`) for weight params — Hibernate sends double precision, PostgreSQL function resolution is strict.

---

## 12. Security Configuration

```java
.requestMatchers(HttpMethod.POST, "/api/v1/ingestion").permitAll()
.requestMatchers(HttpMethod.POST, "/api/v1/ingestion/linkedin-pdf").permitAll()
.requestMatchers(HttpMethod.POST, "/api/v1/search").permitAll()
```

All whitelisted for Stage 1 testing.

---

## 13. application.yaml — all config

```yaml
spring:
  ai:
    model:
      chat: google-genai
      embedding: ollama
    google:
      genai:
        api-key: ${GEMINI_API_KEY}
        chat:
          model: gemini-2.5-flash-lite
          temperature: 0.3
          max-output-tokens: 500

orbit:
  embedding:
    base-url: http://localhost:11434
    model: jeffh/intfloat-multilingual-e5-large:f16
    dimensions: 1024
  github:
    token: ${GITHUB_TOKEN:}
  openalex:
    mailto: andreioleniucroberto@gmail.com
  zefix:
    base-url: https://www.zefix.admin.ch/ZefixPublicREST
    user: ${ZEFIX_USER}
    key: ${ZEFIX_KEY}
```

**Environment variables:**
- `GITHUB_TOKEN` — GitHub PAT
- `GEMINI_API_KEY` — Google Gemini API key
- `ZEFIX_USER` — Zefix API username
- `ZEFIX_KEY` — Zefix API password/key

---

## 14. How It All Connects

```
IngestionController
    │
    ├── SourceAdapterRegistry ──→ GithubAdapter ──── GitHub API (REST + GraphQL)
    │                           ──→ OpenAlexAdapter ── OpenAlex API (ORCID primary)
    │                           ──→ LinkedInPdfAdapter ── PDFBox (local PDF parse)
    │                           ──→ ZefixAdapter ── Zefix REST API (Basic Auth)
    │                           ──→ ManualTextAdapter ── (no fetch, text is content)
    │                           ──→ PersonalWebsiteAdapter ── WebsiteContentExtractor (Jsoup)
    │                           ──→ CompanyWebsiteAdapter ── WebsiteContentExtractor (Jsoup)
    │
    └── IngestionOrchestrator
            ├── SourceAdapter.fetch()          → RawContent
            ├── SourceAdapter.extractHighSignalCore() → coreText
            │       └── LlmSummaryService ──→ ChatModel ──→ Gemini 2.5 Flash-Lite
            ├── SourceAdapter.extractExternalIds() → entity resolution
            ├── ChunkingService.recursiveSplit() → merge tiny + apply overlap
            ├── EmbeddingService.embed("passage: "+text) → float[1024]
            │       └── OllamaEmbeddingService ──→ Ollama (e5-large)
            └── ChunkRepository.save() → PostgreSQL + pgvector

SearchController
    └── SearchService
            ├── EmbeddingService.embed("query: "+text) → float[1024]
            ├── hybrid_search() SQL function → FTS + pgvector + RRF
            └── DiscoveryEntityRepository.findById() → entity profile
```

**Two AI providers, independent:**
- **Chat/Summarization:** Google Gemini 2.5 Flash-Lite (cloud, fast, 4 methods)
- **Embeddings:** Ollama multilingual-e5-large (local, 1024 dims)

---

## 15. Design Decisions Summary

| Decision | Why |
|---|---|
| **SourceAdapter interface** | Adding a source = one class, zero core changes |
| **Per-repo GitHub sources** | Citation chips link to specific repos |
| **Per-work OpenAlex sources** | Citation chips link to specific publications |
| **ORCID as primary OpenAlex input** | Universal/persistent; OpenAlex ID as fallback |
| **Gemini 2.5 Flash-Lite for chat** | Faster (24s vs 36s), reliable (0 retries), auto-configured |
| **Ollama for embeddings** | multilingual-e5-large, local, 1024 dims |
| **DTO subpackages** | Separates pure records from services/interfaces |
| **First-sequential then parallel** | Avoids entity creation race condition |
| **Self-injection (@Lazy)** | Enables @Transactional in parallel CompletableFuture calls |
| **maxTokens=400, overlap=60** | ~15% overlap for e5-large context window |
| **Merge tiny chunks (< 30 tokens)** | Prevents near-empty chunks with minimal semantic signal |
| **OpenAlex metadata enrichment** | Papers without abstracts get year + citations + topic (16→1 tiny chunks) |
| **GitHub LLM metadata description** | Repos without README get Gemini description (13→0 mechanical fallbacks) |
| **Zefix Zweck cleanup via Gemini** | Swiss purposes contain legal boilerplate; Gemini strips it |
| **Manual text Gemini expansion** | Short text (<200 chars) expanded conservatively for better embedding |
| **LinkedIn PDF multi-language** | EN + DE section headers mapped to canonical keys |
| **LinkedIn NBSP fix** | `replace('\u00A0', ' ')` — Java trim/strip don't remove U+00A0 |
| **LinkedIn content-hash idempotency** | Re-uploading same PDF = dedup skip |
| **Website 3-layer boilerplate removal** | Noise containers → main content → structured text with ## headings |
| **Website fallback chain** | Structured → body.text() → raw body text (pre-noise-removal) |
| **Company website CHE UID extraction** | Cross-source entity resolution with Zefix |
| **Domain as external ID** | Personal/company websites use normalized domain for entity resolution |
| **Per-source-type provenance** | Controller maps each source type to correct provenance/consent pair |
| **URI.create() for OpenAlex** | Prevents RestClient from URL-encoding `://` in ORCID paths |
| **Jackson 3.x: properties()** | `fields()` doesn't exist; `properties()` returns `Set<Map.Entry>` |
| **Jackson 3.x: asString()** | New preferred method; `asText()` is final (backward compat) |
| **RRF with rrf_k=50** | Combines FTS + semantic without comparable score scales |
| **max(score) rollup** | Single best chunk wins, not breadth of matches |
| **English FTS** | Content is English READMEs + English abstracts |
| **double precision params** | Hibernate sends double precision; PostgreSQL function resolution strict |
| **Positional ? parameters** | Hibernate parses :name::cast as one parameter name |

---

## 16. Current Database State

**37 entities, 183 sources, 359 chunks, all embedded.**

### Sources by type
| Source type | Sources | Chunks | Avg tokens | Entities |
|---|---|---|---|---|
| GitHub | 93 | 93 | 182 | 8 |
| OpenAlex | 66 | 70 | 173 | 5 |
| Company site | 7 | 105 | 113 | 7 |
| Personal site | 6 | 58 | 223 | 6 |
| LinkedIn PDF | 7 | 29 | 158 | 7 |
| Zefix | 4 | 4 | 34 | 4 |

### Identifiers
| ID type | Count | Used by |
|---|---|---|
| doi | 84 | OpenAlex (per-work) |
| domain | 13 | Personal site + Company site |
| github_login | 8 | GitHub |
| linkedin_url | 7 | LinkedIn PDF |
| openalex_author_id | 5 | OpenAlex |
| orcid | 4 | OpenAlex |
| zefix_uid | 4 | Zefix |

### Chunk quality (after all fixes)
| Issue | Before | After | Fix |
|---|---|---|---|
| GitHub mechanical fallback | 13 chunks | 0 | `describeGithubRepoFromMetadata()` LLM method |
| GitHub tiny chunks (<20) | 6 | 0 | LLM descriptions have 27+ tokens |
| OpenAlex HTML tags | 3 chunks | 0 | `stripHtml()` in extractHighSignalCore() |
| OpenAlex tiny chunks (<20) | 16 | 1 | `mergeTinyChunks()` + metadata enrichment |
| OpenAlex oversize (>400) | 1 | 2 | Acceptable (long abstracts) |

---

## 17. Search Quality Audit (96% pass rate)

**28 queries tested across all 7 source types. 27 found the correct entity in top 5.**

### Results by source type
| Source type | Queries | #1 hits | Top-5 hits | Notes |
|---|---|---|---|---|
| GitHub | 8 | 7 | 8/8 ✅ | Dan Abramov #2 for React (Evan You #1 — both reactive frameworks) |
| OpenAlex | 5 | 5 | 5/5 ✅ | All researchers found at #1 |
| LinkedIn | 2 | 2 | 2/2 ✅ | Marvin von Rappard + full-stack/industrial AI |
| Zefix | 4 | 4 | 4/4 ✅ | Swisscom, Apiax, Soobr, Octotronic (direct query) |
| Company site | 2 | 2 | 2/2 ✅ | Evomi (proxies), Yeastup (yeast upcycling) |
| Personal site | 2 | 1 | 2/2 ✅ | Albino Tonnina #1; photography-staub.ch #2 (Kiese #1) |
| Cross-source | 3 | 3 | 3/3 ✅ | German query, FHNW researcher, industrial AI |
| Broad/negative | 2 | — | — | Correct: no strong matches for quantum physics |

### The 1 "failure" is correct behavior
"Industrial AI automation engineering" → expected Octotronic, got Marvin von Rappard at #1.
- Marvin works at Octotronic building Industrial AI (LinkedIn) — **the right person**
- Octotronic AG's Zefix Zweck is generic ("IT services, software development") — doesn't mention AI
- Direct search for "Octotronic" finds Octotronic AG at #1

---

## 18. Stage Status

| Stage | Status | Description |
|---|---|---|
| **Stage 1 — Ingestion** | ✅ Done (7/7 adapters) | GitHub, OpenAlex, LinkedIn, Zefix, Manual Text, Personal Website, Company Website |
| **Stage 2 — Hybrid Search** | ✅ Done | `hybrid_search()` SQL function, 96% query accuracy |
| **Stage 3 — Geo/Scope** | ❌ Not started | Swiss cantons, proximity radius |
| **Stage 4 — Grounded Generation** | ❌ Not started | LLM profiles from chunks with citation chips |
| **Stage 5 — Availability** | ❌ Not started | Calendar integration, availability windows |
| **Stage 6 — Tags** | ❌ Not started | Skill/industry tag extraction |
| **Stage 7 — Accounts** | ❌ Not started | User accounts, affiliation links, HR verification |
| **Stage 8 — Contact Requests** | ❌ Not started | Match → request → connect flow |

---

## 19. API Reference

### Ingestion

```
POST /api/v1/ingestion
Content-Type: application/json

# GitHub
{"sourceType": "github", "context": {"github_login": "tiangolo"}}

# OpenAlex (ORCID primary)
{"sourceType": "orcid_pub", "context": {"orcid": "0000-0002-0138-892X"}}

# OpenAlex (author ID fallback)
{"sourceType": "orcid_pub", "context": {"openalex_author_id": "A5070738550"}}

# Zefix (by name)
{"sourceType": "zefix", "context": {"company_name": "Apiax AG"}}

# Zefix (by UID)
{"sourceType": "zefix", "context": {"zefix_uid": "CHE-414.461.970"}}

# Manual text
{"sourceType": "manual_text", "context": {"text": "React developer with 5 years experience", "name": "John Doe"}}

# Personal website
{"sourceType": "personal_site", "context": {"url": "https://albinotonnina.com"}}

# Company website
{"sourceType": "company_site", "context": {"url": "https://apiax.com"}}

Response: {"sourceType": "...", "sources": N, "ingested": N, "skipped": N, "errors": N, "totalChunks": N, "results": [...]}
```

```
POST /api/v1/ingestion/linkedin-pdf
Content-Type: multipart/form-data
file=@Profile.pdf

Response: same format
```

### Search

```
POST /api/v1/search
Content-Type: application/json
{"query": "Python web framework FastAPI", "matchCount": 5}

Response: [{
  "entityId": 7,
  "displayName": "Sebastián Ramírez",
  "headline": "Building APIs, FastAPI, Typer, SQLModel",
  "orgName": null,
  "entityType": "person",
  "score": 0.0196,
  "evidence": [{
    "content": "...",
    "section": "...",
    "sourceLabel": "github.com/fastapi/fastapi",
    "sourceUrl": "https://github.com/fastapi/fastapi",
    "tokenCount": 234,
    "similarity": 0.8723
  }]
}]
```