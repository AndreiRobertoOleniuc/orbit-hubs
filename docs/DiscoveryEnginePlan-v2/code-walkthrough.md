# Orbit Ingestion & Search — Complete Code Walkthrough

Every class, every method, every design decision. Organized by data flow — from REST request to search result.

**Last updated:** 2026-07-05 — reflects Gemini switch, DTO restructuring, ORCID as primary OpenAlex input.

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
                                    │                   (Gemini LLM summary or mechanical stripping)
                                    ├─ 3. DEDUP:        sources.findBySourceUrl() + hash check
                                    ├─ 4. RESOLVE:      findEntityByAnyId(extIds) → reuse or create
                                    ├─ 5. UPSERT:       source row (delete old, insert new)
                                    ├─ 6. CHUNK:        ChunkingService.recursiveSplit(coreText, 400, 60)
                                    └─ 7. EMBED+PERSIST: embed("passage: "+text) → vector → save Chunk


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
  ├── SourceAdapter.java          ← interface (stays here)
  ├── SourceAdapterRegistry.java  ← registry (stays here)
  ├── ChunkingService.java        ← service (stays here)
  ├── IngestionOrchestrator.java  ← service (stays here)
  ├── IngestionException.java     ← exception (stays here, NOT a DTO)
  ├── dto/
  │   ├── SourceRef.java           ← moved here
  │   ├── RawContent.java          ← moved here
  │   ├── ExternalId.java          ← moved here
  │   ├── EntityProfile.java       ← moved here
  │   ├── ChunkSegment.java        ← moved here
  │   └── IngestionResult.java     ← moved here
  ├── github/GithubAdapter.java
  └── openalex/OpenAlexAdapter.java

com.orbit.app.search/
  ├── SearchService.java           ← stays here
  ├── SearchController.java        ← stays here
  └── dto/
      └── SearchResult.java        ← moved here (includes nested EvidenceChunk)

com.orbit.app.embedding/
  ├── EmbeddingService.java        ← stays here
  ├── EmbeddingKind.java           ← stays here
  ├── EmbeddingProperties.java     ← stays here
  ├── EmbeddingConfig.java         ← stays here
  ├── OllamaEmbeddingService.java  ← stays here
  └── dto/
      └── EmbeddingDto.java        ← placeholder

com.orbit.app.auth/dto/
  └── AuthContextDto.java          ← placeholder: record(userId, email)

com.orbit.app.profile/dto/
  └── ProfileDto.java              ← placeholder: record(id, displayName)

com.orbit.app.llm/
  └── LlmSummaryService.java       ← only file (ChatConfig.java + LlmProperties.java deleted)

com.orbit.domain.entity/           ← JPA entities (DiscoveryEntity, EntityIdentifier, Source, Chunk)
com.orbit.domain.repository/       ← JPA repositories
com.orbit.controllers/             ← IngestionController
com.orbit.security/                ← ClerkSecurityConfig
```

**Design principle:** `dto` packages contain pure data transfer objects (Java records, immutable, no logic). Parent packages contain services, interfaces, exceptions, and configuration.

---

## 1. REST Entry Points

### IngestionController.java
**Role:** Single REST endpoint for triggering ingestion from any source type.

```
POST /api/v1/ingestion
  Body: {"sourceType": "github", "context": {"github_login": "tiangolo"}}
  Response: {"sources": 15, "ingested": 15, "errors": 0, "totalChunks": 15, ...}
```

**Key methods:**
- `ingest(IngestionRequest)` — receives the request, builds a `SourceRef`, calls `orchestrator.ingestMulti()`, aggregates results into a summary map
- `buildUrl(IngestionRequest)` — constructs the source URL based on source type:
  - `github` → `https://github.com/{github_login}`
  - `orcid_pub` → checks `orcid` first → `https://orcid.org/{orcid}`, falls back to `https://openalex.org/authors/{openalex_author_id}`

**Imports:** `com.orbit.app.ingestion.IngestionOrchestrator`, `com.orbit.app.ingestion.dto.IngestionResult`, `com.orbit.app.ingestion.dto.SourceRef`

**Why:** One endpoint for all source types. The controller doesn't know what a GitHub repo or an OpenAlex work is — it just passes the source type and context to the orchestrator.

### SearchController.java
**Role:** REST endpoint for hybrid search.

```
POST /api/v1/search
  Body: {"query": "Python web framework", "matchCount": 5}
  Response: [{entityId, displayName, headline, score, evidence: [{content, sourceLabel, similarity}]}]
```

**Key methods:**
- `search(Map)` — extracts query and matchCount from the request body, delegates to `SearchService.search()`

**Imports:** `com.orbit.app.search.dto.SearchResult`

**Why:** Stateless, no authentication needed (whitelisted in security config). Just a thin wrapper around the search service.

---

## 2. Value Objects (data flowing through the pipeline)

These are all `record` types — immutable, no logic, just data carriers. All live in `dto` subpackages.

### SourceRef.java — "what to ingest"  (`com.orbit.app.ingestion.dto`)
```java
record SourceRef(
    String url,                    // unique idempotency key (e.g. "https://github.com/torvalds/linux")
    String sourceType,             // "github" | "orcid_pub" | "linkedin_pdf" | "personal_site"
    String provenance,             // "harvested" | "user_supplied" | "self_declared"
    String consentBasis,           // "public_data" | "user_consent" | "self_declared"
    Map<String, String> context    // source-specific params (e.g. {"github_login": "tiangolo", "repo_owner": "fastapi"})
)
```
**Why a Map for context?** Each adapter needs different parameters. GitHub needs `github_login`, `repo_owner`, `repo_name`. OpenAlex needs `orcid`, `work_title`, `work_abstract`. Using a Map avoids a rigid class hierarchy — adapters just read the keys they need.

### RawContent.java — "what was fetched"  (`com.orbit.app.ingestion.dto`)
```java
record RawContent(
    String text,                   // raw text (README content, reconstructed abstract)
    Map<String, Object> metadata,  // structured data (repo name, stars, topics, author profile, etc.)
    Instant fetchedAt
)
```

### ExternalId.java — "who is this person"  (`com.orbit.app.ingestion.dto`)
```java
record ExternalId(String idType, String idValue)
// Examples: ("github_login", "torvalds"), ("orcid", "0000-0001-2345-5678"), ("openalex_author_id", "A5086300447")
```

### EntityProfile.java — "entity creation data"  (`com.orbit.app.ingestion.dto`)
```java
record EntityProfile(
    String entityType,             // "person" | "organization"
    String displayName,
    String headline,
    String orgName,
    Double latitude,               // null in Stage 1 (geocoding in Stage 3)
    Double longitude,
    Map<String, Object> metadata   // extra data (location, blog, orcid, etc.)
)
```

### ChunkSegment.java — "a piece of text ready to embed"  (`com.orbit.app.ingestion.dto`)
```java
record ChunkSegment(
    String text,                   // the chunk content
    String section,                // context label (e.g. "Parallel Computing", "Features", null)
    int tokenCount                 // estimated BERT token count
)
```

### IngestionResult.java — "what happened"  (`com.orbit.app.ingestion.dto`)
```java
record IngestionResult(
    Long entityId,
    Long sourceId,
    int chunkCount,
    Status status                  // INGESTED | SKIPPED | ERROR
)
```

### IngestionException.java — "what went wrong"  (`com.orbit.app.ingestion` — NOT in dto, it's an exception)
Simple `RuntimeException` subclass. Thrown by adapters when API calls fail. Caught by the orchestrator, which logs and returns an ERROR result.

---

## 3. Source Adapter Pattern

### SourceAdapter.java — the interface  (`com.orbit.app.ingestion`)
```java
public interface SourceAdapter {
    String sourceType();                              // "github", "orcid_pub"
    RawContent fetch(SourceRef ref);                   // get the raw content
    String extractHighSignalCore(RawContent raw);      // strip noise → embeddable text
    String buildLabel(RawContent raw);                 // citation chip text
    List<ExternalId> extractExternalIds(RawContent raw); // entity resolution keys
    EntityProfile deriveProfile(RawContent raw);       // entity creation data

    default List<SourceRef> discoverSources(SourceRef ref) {  // multi-source discovery
        return List.of(ref);                                  // default: one source
    }
}
```

**Imports DTOs from `com.orbit.app.ingestion.dto`**: `EntityProfile`, `ExternalId`, `RawContent`, `SourceRef`.

**Design decision:** The `discoverSources()` method has a default implementation returning a single-element list. This means:
- **Single-source adapters** (LinkedIn PDF, personal website) don't override it — they just implement the other 6 methods
- **Multi-source adapters** (GitHub, OpenAlex) override it to discover N sub-sources (repos, publications)

**Adding a new source = implement this interface + `@Component`.** The registry auto-discovers it. Zero changes to the orchestrator.

### SourceAdapterRegistry.java — adapter lookup  (`com.orbit.app.ingestion`)
```java
@Component
public class SourceAdapterRegistry {
    private final Map<String, SourceAdapter> adapters;

    // Spring injects all SourceAdapter beans → map by sourceType()
    public SourceAdapterRegistry(List<SourceAdapter> adapterBeans) {
        this.adapters = adapterBeans.stream()
            .collect(Collectors.toMap(SourceAdapter::sourceType, Function.identity()));
    }

    public SourceAdapter getAdapter(String sourceType) { ... }
}
```

**How it works:** Spring's constructor injection gives us all beans implementing `SourceAdapter`. We map them by `sourceType()` string ("github" → GithubAdapter, "orcid_pub" → OpenAlexAdapter). When the orchestrator needs an adapter, it calls `registry.getAdapter("github")`.

---

## 4. Adapters

### GithubAdapter.java  (`com.orbit.app.ingestion.github`)

**sourceType() = "github"**

**Imports:** `com.orbit.app.ingestion.dto.{EntityProfile, ExternalId, RawContent, SourceRef}`, `com.orbit.app.ingestion.{IngestionException, SourceAdapter}`, `com.orbit.app.llm.LlmSummaryService`

**discoverSources() — the multi-source entry point:**
1. `fetchUser(username)` — GET `/users/{username}` → profile (name, bio, company, location)
2. `fetchPinnedRepos(username)` — GraphQL `user.pinnedItems(first: 6)` → up to 6 pinned repos (can be from other orgs like `fastapi/fastapi`)
3. `searchReposByStars(username)` — GET `/search/repositories?q=user:{username}+fork:false&sort=stars` → top personal repos by stars
4. Merge: pinned first, then personal (excluding duplicates), up to 15 total
5. Return one `SourceRef` per repo, with repo metadata cached in context
6. First repo's context also has profile data (display_name, bio, company) for entity creation

**fetch() — get README for one repo:**
1. `fetchRepo(owner, repo)` — GET `/repos/{owner}/{repo}` → repo metadata (topics, stars, language)
2. `fetchReadme(owner, repo)` — GET `/repos/{owner}/{repo}/readme` with `Accept: application/vnd.github.raw`
3. Return `RawContent` with README text + all metadata

**extractHighSignalCore() — Gemini LLM summarization with fallback:**
1. Try `llmSummaryService.summarizeGithubRepo()` → 200-300 token summary (via Gemini 2.5 Flash-Lite)
2. If LLM fails (all 3 retries exhausted), fall back to `buildMechanicalCoreText()` — strips badges/HTML from README
3. If no README, use description only

**stripReadmeBoilerplate() — mechanical fallback:**
Regex-based cleanup:
- Remove badge images (`![...](https://img.shields.io/...)`)
- Remove GitHub Actions workflow badges
- Remove HTML comments and tags
- Remove YAML code blocks (CI configs)
- Collapse multiple blank lines

**extractExternalIds() → `("github_login", username)`**
All repos from the same user share the same `github_login`, so entity resolution finds the existing entity for repos 2-15.

**deriveProfile() → name from GitHub profile, headline from bio or company**

**Jackson 3.x note:** In Jackson 3.1.2 (`tools.jackson.databind`), `asString()` is the new preferred method (abstract, meant to be overridden) and `asText()` is the old one (final, kept for backward compat). Both work. The `searchReposByStars()` method uses `asString()`; the rest of the adapter still uses `asText()` — both are valid.

**Value objects:**
- `GithubUser(login, displayName, bio, company, location, blog, publicRepos, followers)`
- `GithubRepo(ownerLogin, name, description, language, topics, stargazersCount, pushedAt)`

---

### OpenAlexAdapter.java  (`com.orbit.app.ingestion.openalex`)

**sourceType() = "orcid_pub"**

**Imports:** Same pattern as GithubAdapter — DTOs from `com.orbit.app.ingestion.dto`, `IngestionException` and `SourceAdapter` from `com.orbit.app.ingestion`.

**discoverSources() — fetch author + works:**
1. `resolveAuthorId(ref)` — **ORCID is the primary input**. Checks `orcid` context key first (e.g. "0000-0002-0138-892X"). Falls back to `openalex_author_id` (e.g. "A5086300447") if ORCID is not provided. Strips URL prefixes from both.
2. `fetchAuthor(authorId)` — if ID starts with "A" → `GET /authors/{id}` (OpenAlex ID path). Otherwise → `GET /authors/https://orcid.org/{orcid}` (ORCID path). Returns profile (name, ORCID, affiliation, works_count) + the OpenAlex author ID (extracted from the response).
3. `fetchWorks(authorOpenAlexId)` — `GET /works?filter=author.id:{openalexId}&sort=cited_by_count:desc&per_page=25` → top-cited works
4. For each work: reconstruct abstract from inverted index, build SourceRef with cached work data
5. All works' context includes `openalex_author_id` and `orcid` (if available) for entity resolution
6. First work's context also has author profile (display_name, affiliation) for entity creation

**The ORCID flow:**
```
User provides: {"orcid": "0000-0002-0138-892X"}
  ↓ resolveAuthorId() → "0000-0002-0138-892X"
  ↓ fetchAuthor() → GET /authors/https://orcid.org/0000-0002-0138-892X
  ↓ OpenAlex returns author profile including OpenAlex ID (A5037356139)
  ↓ fetchWorks() → GET /works?filter=author.id:A5037356139
  ↓ External IDs stored: orcid (primary) + openalex_author_id (secondary) + doi (per-work)
```

**The fallback flow (no ORCID):**
```
User provides: {"openalex_author_id": "A5070738550"}
  ↓ resolveAuthorId() → "A5070738550" (starts with "A" → OpenAlex ID path)
  ↓ fetchAuthor() → GET /authors/A5070738550
```

**fetch() — return cached work data (no API call):**
All work data was cached in the SourceRef context during `discoverSources()`. `fetch()` just wraps it in a `RawContent` — zero additional API calls.

**extractHighSignalCore() — build core text:**
```
## {primary_topic.display_name}     ← becomes chunk section via ChunkingService
{title}
{reconstructed_abstract}
```
The `##` heading is key — ChunkingService's `splitBySections()` detects it and sets the chunk's `section` field.

**reconstructAbstract() — reverse the inverted index:**
```java
// OpenAlex stores: {"word": [0, 5], "another": [1], ...}
// We reconstruct: word array → join with spaces → plain text
```
1. First pass: iterate `invertedIndex.properties()` (returns `Set<Map.Entry<String, JsonNode>>` in Jackson 3.x), find max position
2. Second pass: place each word at its position(s)
3. Join with spaces → original abstract text

**Jackson 3.x note:** `JsonNode.fields()` and `fieldNames()` don't exist in Jackson 3.x. Use `properties()` which returns `Set<Map.Entry<String, JsonNode>>`. Iterate with for-each, not `Iterator.hasNext()`.

**extractExternalIds() → ORCID (primary) + openalex_author_id (secondary) + doi (per-work)**
ORCID is listed first because it's the universal, persistent researcher identifier. `openalex_author_id` is always available (even when ORCID is missing), so all works from the same author resolve to one entity.

**deriveProfile() → name + affiliation from OpenAlex author profile**

**fetchJson() — URI.create() fix:**
```java
private JsonNode fetchJson(String path) {
    String fullPath = path;
    if (mailto != null && !mailto.isBlank()) {
        fullPath += (path.contains("?") ? "&" : "?") + "mailto=" + mailto;
    }
    // Use URI.create to prevent RestClient from re-encoding the ORCID URL in the path
    // (e.g. /authors/https://orcid.org/... — the :// must stay literal)
    java.net.URI uri = java.net.URI.create(API_BASE + fullPath);
    return restClient.get().uri(uri).retrieve().body(JsonNode.class);
}
```
**Why URI.create()?** RestClient URL-encodes string paths, turning `://` into `%3A%2F%2F` which breaks the OpenAlex ORCID lookup URL. `URI.create()` passes the pre-built URI without re-encoding.

**Config:** `orbit.openalex.mailto` → added as `?mailto=email` query param for polite pool (no API key needed)

**Value objects:**
- `OpenAlexAuthor(openalexId, displayName, orcid, affiliation, worksCount)`
- `OpenAlexWork(openalexId, title, publicationYear, doi, type, citedByCount, primaryTopic, reconstructedAbstract)`

---

## 5. Core Pipeline

### IngestionOrchestrator.java  (`com.orbit.app.ingestion`, 280 lines)

**Imports:** `com.orbit.app.ingestion.dto.*` (wildcard for all DTOs)

This is the heart of the pipeline. It's source-agnostic — it doesn't know what a GitHub repo or an OpenAlex work is. It just calls the adapter methods.

**ingestMulti(SourceRef ref) — multi-source entry point:**
```
1. adapter.discoverSources(ref) → List<SourceRef> (e.g. 15 repos, 25 works)
2. If 1 source → just call self.ingest() directly
3. If >1 source:
   a. First sub-source: SEQUENTIAL via self.ingest() → creates entity
   b. Remaining sub-sources: PARALLEL via CompletableFuture + 5-thread pool
   c. Each parallel call goes through self (Spring proxy) for @Transactional
4. Aggregate results
```

**Why first-then-parallel?** All sub-sources share the same external ID (`github_login` or `orcid`/`openalex_author_id`). The first source creates the entity. If we ran all in parallel, two might simultaneously try to create the same entity → race condition. Running the first one sequentially guarantees the entity exists before the parallel calls start.

**Self-injection (@Lazy):** The orchestrator injects itself via `@Lazy IngestionOrchestrator self`. This is because `ingest()` is `@Transactional`, and Spring's transaction proxy only works when called through the proxy (not via `this.ingest()`). So parallel calls use `self.ingest(sub)` which goes through the Spring proxy.

**ingest(SourceRef ref) — the per-source pipeline [@Transactional]:**

```
Step 1: FETCH
    raw = adapter.fetch(ref)
    → adapter makes API call(s), returns RawContent with text + metadata

Step 2: EXTRACT
    coreText = adapter.extractHighSignalCore(raw)
    → Gemini LLM summary (GitHub) or primary_topic + title + abstract (OpenAlex)
    hash = sha256(normalize(coreText))

Step 3: DEDUP (idempotent short-circuit)
    existing = sources.findBySourceUrl(ref.url())
    if existing.hash == hash → return SKIPPED (no re-embedding needed)

Step 4: RESOLVE ENTITY
    extIds = adapter.extractExternalIds(raw)
    entityId = findEntityByAnyId(extIds)
    if not found:
        profile = adapter.deriveProfile(raw)
        entity = new DiscoveryEntity(profile.entityType, profile.displayName, ...)
        save entity
        save all extIds as EntityIdentifier rows
    else:
        reuse existing entity
        save any new extIds not yet in entity_identifiers

Step 5: UPSERT SOURCE
    delete old source (if exists)
    save new Source(entityId, sourceType, url, label, rawContent, hash, ...)

Step 6: RE-CHUNK
    chunks.deleteBySourceId(source.id)  ← delete old chunks
    segments = chunkingService.recursiveSplit(coreText, 400, 60)

Step 7: EMBED + PERSIST
    for each segment:
        vec = embeddingService.embed("passage: " + segment.text(), PASSAGE)
        save Chunk(sourceId, entityId, index, text, section, tokenCount, vec, ...)
```

**Key design decisions:**
- **Idempotency:** `source_url` is unique. Re-ingesting the same URL upserts the source and rebuilds chunks. Content hash short-circuits if nothing changed.
- **Content hash:** `sha256(normalize(coreText))` where `normalize()` trims, collapses whitespace, lowercases. Prevents re-embedding when only whitespace changed.
- **Re-chunk on every ingest:** Old chunks are deleted and new ones created. This ensures chunks always match the current core text.

---

## 6. Chunking

### ChunkingService.java  (`com.orbit.app.ingestion`, 393 lines)

**Role:** Split text into chunks of ≤400 tokens with 60-token overlap.

**recursiveSplit(text, maxTokens, overlapTokens) → List<ChunkSegment>:**
```
1. splitRecursive(text, maxTokens, depth=0)
   ├─ depth 0: try splitBySections (## headings)
   │   ├─ each section = heading + content until next heading
   │   ├─ section label = heading text (truncated to 80 chars)
   │   └─ if section > maxTokens → recurse with depth=1 (paragraphs)
   ├─ depth 1: try splitByPattern(PARAGRAPH_BOUNDARY = \n\s*\n)
   │   └─ merge paragraphs until maxTokens, recurse if one paragraph too big
   ├─ depth 2: try splitByPattern(SENTENCE_BOUNDARY = [.!?]\s+[A-Z])
   │   └─ merge sentences until maxTokens, recurse if one sentence too big
   └─ depth 3: splitByWords (word boundaries, preserving original whitespace)

2. applyOverlap(chunks, overlapTokens, maxTokens)
   └─ prepend last ~60 tokens of previous chunk to current chunk
```

**CRITICAL design decision: preserve original whitespace.**

All splitting methods use `text.substring(start, end)` to extract text. The `tokenize()` method is used ONLY for counting tokens, never for reconstructing text. This prevents bugs like "FastAPIisamodern" (missing spaces) that we fixed earlier.

**Token counting:** `countTokens(text) = tokenize(text).size()`
The `tokenize()` method approximates BERT WordPiece tokenization:
- Split on whitespace → words
- Split each word at punctuation → tokens
- Example: "Fast, framework!" → ["Fast", ",", "framework", "!"]

**Section detection:**
- `HEADING_PATTERN = ^##[^#].+` — only level-2 headings (##), not ### or #
- This was changed from `^#{1,6}` to avoid splitting on sub-headings
- Each section includes the heading + all content until the next ##

**Why ## and not #?** GitHub READMEs use # for the title and ## for sections. We want to split at section level, not at the title. OpenAlex content uses ## for the primary_topic.

---

## 7. Embedding (via Ollama — unchanged)

### EmbeddingService.java — interface  (`com.orbit.app.embedding`)
```java
public interface EmbeddingService {
    float[] embed(String text, EmbeddingKind kind);  // QUERY or PASSAGE
}
```

### EmbeddingKind.java — enum  (`com.orbit.app.embedding`)
```java
public enum EmbeddingKind { QUERY, PASSAGE }
```

### OllamaEmbeddingService.java — implementation  (`com.orbit.app.embedding`)
```java
@Override
public float[] embed(String text, EmbeddingKind kind) {
    String prefixed = switch (kind) {
        case QUERY    → props.queryPrefix() + text;    // "query: "
        case PASSAGE  → props.passagePrefix() + text;  // "passage: "
    };
    float[] vector = embeddingModel.embed(prefixed);
    // Validates dimension (1024)
    return vector;
}
```

**Why prefixes?** `multilingual-e5-large` is a contrastive model — it was trained with "query: " and "passage: " prefixes. Without them, embeddings are meaningless. Queries get "query: ", stored chunks get "passage: ".

### EmbeddingProperties.java — config  (`com.orbit.app.embedding`)
```yaml
orbit.embedding:
  base-url: http://localhost:11434
  model: jeffh/intfloat-multilingual-e5-large:f16
  dimensions: 1024
  query-prefix: "query: "
  passage-prefix: "passage: "
```

### EmbeddingConfig.java — Spring config  (`com.orbit.app.embedding`)
Creates two beans:
- `OllamaApi` — low-level Ollama API client
- `OllamaEmbeddingModel` — Spring AI embedding model wrapping OllamaApi

---

## 8. LLM Summarization (via Google Gemini 2.5 Flash-Lite)

### LlmSummaryService.java  (`com.orbit.app.llm`, 114 lines)

**Role:** Distill GitHub READMEs into clean, focused summaries using the LLM.

**summarizeGithubRepo(repoName, description, language, topics, readme):**
1. Build prompt asking for 200-300 word summary (what project does, technologies, expertise)
2. Retry loop (3 attempts):
   - Call `chatModel.call(new Prompt(prompt))` → get response text
   - If success → return text
   - If exception → log warning, wait (2s × attempt), retry
3. If all 3 attempts fail → return null (adapter falls back to mechanical stripping)

**Prompt design:**
```
"You are a technical summarizer for a semantic search system...
Summarize this GitHub repository in 200-300 words. Focus on:
- What the project does
- What technologies it uses
- What expertise it demonstrates
- Notable features

Write in plain text (no markdown, no headers, no bullet points)..."
```

**Why this works:** The LLM strips noise (badges, CI configs, sponsor links) and produces a dense, searchable summary. The mechanical fallback preserves everything, including noise. LLM summaries = 1 chunk per repo with 100% signal. Mechanical = 4-53 chunks per repo with noise.

### Chat Model Configuration — Auto-configured by Spring AI

**No manual config class needed.** The `spring-ai-starter-model-google-genai` starter auto-configures a `GoogleGenAiChatModel` bean that implements the `ChatModel` interface. `LlmSummaryService` injects `ChatModel` (interface), so it gets the Gemini model automatically.

**Previous config (deleted):**
- `ChatConfig.java` — deleted (was manual Ollama chat model beans)
- `LlmProperties.java` — deleted (replaced by Spring AI built-in properties)

**Switch history:** Ollama `gpt-oss:20b-cloud` → Google Gemini 2.5 Flash-Lite. Gemini is faster (1.2-7.2s per call vs 4.4-9.1s) and more reliable (0 retries vs 3 retries for 15 repos). Total time: 24s vs 36s.

**Performance comparison:**
| Metric | Ollama (before) | Gemini (after) |
|---|---|---|
| Total time (15 repos) | 36s | 24s |
| Retries needed | 3 repos (attempt 2) | 0 (all attempt 1) |
| LLM call times | 4.4-9.1s | 1.2-7.2s |
| Chunk quality | Clean summaries | Clean summaries |

---

## 9. JPA Entities (database mapping)

### DiscoveryEntity.java → `entities` table  (`com.orbit.domain.entity`)
| Field | Column | Notes |
|---|---|---|
| `id` | `id` | Auto-generated bigint |
| `entityType` | `entity_type` | "person" or "organization" |
| `displayName` | `display_name` | Not null |
| `headline` | `headline` | Bio or affiliation |
| `orgName` | `org_name` | Company/university |
| `latitude/longitude` | `latitude/longitude` | Null in Stage 1 |
| `status` | `status` | Default "active" |
| `metadata` | `metadata` | JSONB (location, blog, orcid) |

**Key annotation:** `@JdbcTypeCode(SqlTypes.JSON)` on `metadata` — maps JSONB column in PostgreSQL.

### EntityIdentifier.java → `entity_identifiers` table  (`com.orbit.domain.entity`)
| Field | Column | Notes |
|---|---|---|
| `entityId` | `entity_id` | FK to entities |
| `idType` | `id_type` | "github_login", "orcid", "openalex_author_id", "doi" |
| `idValue` | `id_value` | The actual ID value |

**Unique constraint:** `(id_type, id_value)` — one identifier can only map to one entity.

### Source.java → `sources` table  (`com.orbit.domain.entity`)
| Field | Column | Notes |
|---|---|---|
| `entityId` | `entity_id` | FK to entities |
| `sourceType` | `source_type` | "github", "orcid_pub" |
| `sourceUrl` | `source_url` | Unique — idempotency key |
| `label` | `label` | Citation chip text |
| `rawContent` | `raw_content` | Full text (README or abstract) |
| `contentHash` | `content_hash` | SHA-256 of normalized core text |

### Chunk.java → `chunks` table  (`com.orbit.domain.entity`)
| Field | Column | Notes |
|---|---|---|
| `sourceId` | `source_id` | FK to sources |
| `entityId` | `entity_id` | Denormalized for scoped filtering |
| `chunkIndex` | `chunk_index` | Position within source |
| `content` | `content` | The chunk text |
| `section` | `section` | Context label (heading text) |
| `tokenCount` | `token_count` | Estimated BERT tokens |
| `embedding` | `embedding` | `vector(1024)` via pgvector |
| `fts` | `fts` | Generated tsvector (not in entity — DB-generated) |

**Key annotation:** `@Column(name = "embedding", columnDefinition = "vector(1024)")` with `float[]` type. Hibernate's pgvector support handles the conversion.

---

## 10. Repositories  (`com.orbit.domain.repository`)

All extend `JpaRepository` — standard Spring Data JPA.

### DiscoveryEntityRepository.java
```java
// Just JpaRepository — no custom methods needed yet
// findById() comes from JpaRepository
```

### EntityIdentifierRepository.java
```java
// Find entity by (id_type, id_value) — used during entity resolution
@Query("select ei.entityId from EntityIdentifier ei where ei.idType = :idType and ei.idValue = :idValue")
Optional<Long> findEntityIdByIdTypeAndIdValue(String idType, String idValue);

// Check if identifier exists — avoid duplicate inserts
boolean existsByIdTypeAndIdValue(String idType, String idValue);
```

### SourceRepository.java
```java
// Find by URL — idempotency check
Optional<Source> findBySourceUrl(String sourceUrl);
```

### ChunkRepository.java
```java
// Delete all chunks for a source before re-chunking
@Modifying
@Query("delete from Chunk c where c.sourceId = :sourceId")
void deleteBySourceId(Long sourceId);
```

---

## 11. Search

### SearchService.java  (`com.orbit.app.search`, 166 lines)

**Imports:** `com.orbit.app.search.dto.SearchResult`

**search(queryText, matchCount) → List<SearchResult>:**

```
Step 1: EMBED QUERY
    float[] queryEmbedding = embeddingService.embed("query: " + queryText, QUERY)
    String vectorStr = toVectorString(queryEmbedding)  // "[0.123,0.456,...]"

Step 2: HYBRID SEARCH (SQL function)
    SELECT entity_id, score FROM hybrid_search(
        CAST(? AS text),        -- query text
        ?::vector,              -- query embedding
        CAST(? AS int),          -- match count
        1.0::float8, 1.0::float8, -- weights
        50                       -- rrf_k
    )
    → List<(entity_id, score)>

Step 3: FETCH EVIDENCE (per entity)
    For each (entity_id, score):
        a. Load DiscoveryEntity from repository
        b. Fetch top-3 chunks (deduped by source_id):
           SELECT DISTINCT ON (s.id)
                  c.content, c.section, c.token_count,
                  s.label, s.source_url,
                  (c.embedding <#> ?::vector) AS distance
           FROM chunks c JOIN sources s ON c.source_id = s.id
           WHERE c.entity_id = ?
           ORDER BY s.id, c.embedding <#> ?::vector  -- best chunk per source
           → subquery ORDER BY distance LIMIT 3     -- top 3 by similarity

        c. Convert distance to similarity: similarity = -distance
           (<#> returns negative inner product, so -distance = positive similarity)

    → SearchResult(entityId, displayName, headline, score, evidence)
```

**Why positional parameters (?):** Hibernate parses `:namedParam::cast` as a single parameter name. Using `?` with `setParameter(1, value)` avoids this. The `::vector`, `::text`, `::float8` casts are SQL syntax, not Hibernate parameter names.

### SearchResult.java — response DTO  (`com.orbit.app.search.dto`)
```java
record SearchResult(
    Long entityId,
    String displayName,
    String headline,
    String orgName,
    String entityType,
    double score,                    // RRF score
    List<EvidenceChunk> evidence     // top-3 matching chunks
) {
    record EvidenceChunk(
        String content,              // chunk text
        String section,              // section label
        String sourceLabel,          // citation chip (e.g. "github.com/torvalds/linux")
        String sourceUrl,            // link to source
        int tokenCount,
        double similarity            // cosine similarity (-distance)
    ) {}
}
```

---

## 12. Database

### V2__discovery_engine_stage1.sql

Creates 4 tables + indexes:

```sql
entities              -- id, entity_type, display_name, headline, org_name, lat/lng, status, metadata (JSONB)
entity_identifiers    -- id, entity_id, id_type, id_value (UNIQUE)
sources               -- id, entity_id, source_type, source_url (UNIQUE), label, raw_content, content_hash
chunks                -- id, source_id, entity_id, chunk_index, content, section, token_count,
                      --   fts (GENERATED tsvector, 'german' originally), embedding (vector(1024)), embedding_model
```

**Indexes:**
- `entities(status)` — filter active entities
- `entity_identifiers(entity_id)` — find all IDs for an entity
- `sources(entity_id)` — find all sources for an entity
- `sources(source_type)` — filter by type
- `chunks(source_id)` — find/delete chunks for a source
- `chunks(entity_id)` — scoped filtering
- `chunks USING gin (fts)` — full-text search index
- `chunks USING hnsw (embedding vector_ip_ops)` — vector similarity search (HNSW = Hierarchical Navigable Small World)

### V3__hybrid_search_function.sql

1. **Fix FTS language:** Drop `fts` column (was `'german'`), recreate with `'english'` (our content is English GitHub READMEs + English abstracts)

2. **hybrid_search function:**
```sql
CREATE FUNCTION hybrid_search(
  query_text text, query_embedding vector(1024), match_count int DEFAULT 10,
  full_text_weight double precision DEFAULT 1.0,
  semantic_weight double precision DEFAULT 1.0,
  rrf_k int DEFAULT 50
) RETURNS TABLE(entity_id bigint, score double precision)

-- Two legs, fused by RRF:
-- 1. full_text:  chunks WHERE fts @@ websearch_to_tsquery('english', q), ranked by ts_rank_cd
-- 2. semantic:    chunks ORDER BY embedding <#> query_embedding (negative inner product)
-- 3. fused:       score = full_text_weight/(rrf_k + fts_rank) + semantic_weight/(rrf_k + sem_rank)
-- 4. rollup:      max(score) per entity — the single best matching chunk wins
-- 5. limit:       top match_count entities
```

**Key technical notes:**
- Uses `double precision` (not `float`) for weight params — Hibernate sends double precision, and PostgreSQL function resolution is strict about types
- `DROP FUNCTION` requires exact type names: `integer` not `int`
- Function was manually applied to the database (Flyway had issues with `CREATE OR REPLACE` when the old function had a different signature)

**RRF (Reciprocal Rank Fusion):** Each leg contributes `weight / (rrf_k + rank)`. With `rrf_k=50`, rank 1 gives `1/51 ≈ 0.0196`, rank 2 gives `1/52 ≈ 0.0192`. This combines two different score scales (ts_rank_cd vs inner product) without needing them to be comparable — only the ranks matter.

**max(score) rollup:** An entity with one excellent chunk scores higher than an entity with many mediocre chunks. This matches the thesis: "the single strongest matching chunk wins."

---

## 13. Security Configuration  (`com.orbit.security`)

In `ClerkSecurityConfig.java`:
```java
.requestMatchers(HttpMethod.POST, "/api/v1/ingestion").permitAll()
.requestMatchers(HttpMethod.POST, "/api/v1/search").permitAll()
```

Both endpoints are whitelisted (no JWT required) for Stage 1 testing. In production, ingestion will require admin auth, and search may be public or authenticated.

---

## 14. application.yaml — all config

```yaml
spring:
  ai:
    model:
      chat: google-genai              # Use Google Gemini for chat (LLM summarization)
      embedding: ollama               # Keep Ollama for embeddings (multilingual-e5-large)
    google:
      genai:
        api-key: ${GEMINI_API_KEY}    # from environment variable
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
    token: ${GITHUB_TOKEN:}           # from environment variable
  openalex:
    mailto: andreioleniucroberto@gmail.com  # polite pool, no API key
```

**What changed:**
- `orbit.llm.*` config block — **removed** (was for Ollama chat, now handled by `spring.ai.google.genai.*`)
- `spring.ai.model.chat=google-genai` — **added** (enables Gemini, disables Ollama chat)
- `spring.ai.model.embedding=ollama` — **added** (keeps Ollama for embeddings)
- `spring.ai.google.genai.*` — **added** (Gemini API key, model, temperature, max tokens)

**Environment variables:**
- `GITHUB_TOKEN` — GitHub PAT for GraphQL pinned repos + rate limits
- `GEMINI_API_KEY` — Google Gemini API key
- `OLLAMA_BASE_URL` — Ollama base URL (default: `http://localhost:11434`)

---

## 15. How It All Connects

```
IngestionController
    │
    ├── SourceAdapterRegistry ──→ GithubAdapter ──── GitHub API (REST + GraphQL)
    │                           ──→ OpenAlexAdapter ── OpenAlex API (REST, ORCID primary)
    │
    └── IngestionOrchestrator
            │
            ├── SourceAdapter.fetch()          → RawContent (from dto package)
            ├── SourceAdapter.extractHighSignalCore() → coreText
            │       │
            │       └── LlmSummaryService ──→ ChatModel ──→ Google Gemini 2.5 Flash-Lite
            │                                     (auto-configured by spring-ai-starter-model-google-genai)
            │
            ├── SourceAdapter.extractExternalIds() → List<ExternalId> (from dto package)
            │       │
            │       └── EntityIdentifierRepository.findEntityIdByIdTypeAndIdValue()
            │           → reuse entity or create new via DiscoveryEntityRepository
            │
            ├── SourceRepository.findBySourceUrl() → dedup check
            │
            ├── ChunkingService.recursiveSplit() → List<ChunkSegment> (from dto package)
            │
            ├── EmbeddingService.embed("passage: " + text) → float[1024]
            │       │
            │       └── OllamaEmbeddingService ──→ OllamaEmbeddingModel ──→ Ollama (e5-large)
            │
            └── ChunkRepository.save() → Chunk with embedding → PostgreSQL + pgvector

SearchController
    │
    └── SearchService
            ├── EmbeddingService.embed("query: " + text) → float[1024]
            │       └── OllamaEmbeddingService ──→ Ollama (e5-large)
            ├── hybrid_search() SQL function → PostgreSQL (FTS + pgvector + RRF)
            └── DiscoveryEntityRepository.findById() → entity profile
```

**Two AI providers, independent:**
- **Chat/Summarization:** Google Gemini 2.5 Flash-Lite (API key auth, cloud, fast)
- **Embeddings:** Ollama multilingual-e5-large (local, 1024 dims)

---

## 16. Design Decisions Summary

| Decision | Why |
|---|---|
| **SourceAdapter interface** | Adding a source = one class, zero core changes |
| **Per-repo GitHub sources** | Citation chips link to specific repos, not a profile |
| **Per-work OpenAlex sources** | Citation chips link to specific publications |
| **ORCID as primary OpenAlex input** | Universal, persistent researcher identifier. OpenAlex ID as fallback for authors without ORCID |
| **LLM summarization for GitHub** | READMEs are unstructured noise; LLM produces 1 clean chunk per repo |
| **Gemini 2.5 Flash-Lite for chat** | Faster (24s vs 36s), more reliable (0 retries vs 3), auto-configured by Spring AI |
| **Ollama for embeddings (unchanged)** | multilingual-e5-large, local, 1024 dims, "query: "/"passage: " prefixes |
| **Abstract reconstruction for OpenAlex** | OpenAlex stores inverted indices, not plain text |
| **DTO subpackages** | Separates pure data records from services/interfaces/exceptions |
| **First-sequential then parallel** | Avoids entity creation race condition |
| **Self-injection (@Lazy)** | Enables @Transactional in parallel CompletableFuture calls |
| **maxTokens=400, overlap=60** | ~15% overlap, tuned for e5-large's context window |
| **## headings only** | # is title, ## is sections, ### is sub-sections |
| **substring() for all splitting** | Preserves original whitespace (never join tokens) |
| **"passage: "/"query: " prefixes** | e5-large is contrastive — prefixes are mandatory |
| **RRF with rrf_k=50** | Combines FTS + semantic without needing comparable score scales |
| **max(score) rollup** | Single best chunk wins, not breadth of matches |
| **English FTS** | Our content is English GitHub READMEs + English abstracts |
| **double precision (not float)** | Hibernate sends double precision; PostgreSQL function resolution is strict |
| **Positional ? parameters** | Hibernate parses :name::cast as one parameter name |
| **URI.create() for OpenAlex fetchJson** | Prevents RestClient from URL-encoding `://` in the ORCID path |
| **Jackson 3.x: properties() not fields()** | `fields()` and `fieldNames()` don't exist; `properties()` returns `Set<Map.Entry>` |
| **Jackson 3.x: asString() preferred** | New abstract method; `asText()` is final (backward compat) |