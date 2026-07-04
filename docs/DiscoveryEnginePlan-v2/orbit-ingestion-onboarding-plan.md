# Orbit — Entity Onboarding & Multi-Source Ingestion Plan

> **Companion to:** `orbit-discovery-engine-reference.md` (v2). This plan implements **Stage 1** of the incremental schema and the ingestion pipeline described in §4 of the reference.

## 1. Overview & Goals

### 1.1 What we're building

A multi-source ingestion pipeline that flows content from heterogeneous external sources (GitHub, LinkedIn PDF, ORCID via OpenAlex, personal websites) into the **`entity → source → chunk`** three-tier hierarchy. The pipeline must be:

- **Idempotent** — re-ingesting the same source is a no-op if nothing changed (`sources.content_hash`)
- **Convergent** — multiple sources for the same person land on one entity (`entity_identifiers`)
- **High-quality** — only high-signal text is embedded; boilerplate is stripped before chunking
- **Extensible** — new source types can be added by implementing one interface, without touching the core pipeline

### 1.2 Why this matters

Every chunk that enters the DB is a future retrieval candidate. The quality of ingestion directly determines the quality of the downstream **retrieve → re-rank → ground** pipeline. Bad ingestion = noisy chunks = poor retrieval = hallucinated or irrelevant rationales. The grounding invariant ("a citation can never reference evidence that wasn't retrieved") means our chunks ARE the ceiling on match quality.

### 1.3 Scope: Stage 1 only

This plan covers **only** Stage 1 tables and logic:

| Table | Purpose |
|---|---|
| `entities` | The thing we rank & show on a card |
| `entity_identifiers` | Entity resolution / dedup (one ORCID = one entity) |
| `sources` | One evidence feed per entity; also the citation layer |
| `chunks` | The searchable unit (embedding + fts) |

Later stages (regions, hubs, tags, anfragen, matches) build on top of this. The ingestion algorithm already has derivation hooks (`proposeTags`, `proposeHub`, `setRegionIfKnown`, `setAffiliationIfKnown`) that will activate in later stages — they are stubs in Stage 1.

---

## 2. Architecture: Source Adapter Pattern

### 2.1 The core insight

Every source — whether it's an API call, a PDF upload, or a web crawl — produces the same three artifacts:

1. **Raw content** (the fetched bytes/text)
2. **High-signal core text** (boilerplate-stripped, embedding-ready)
3. **External identifiers** (for entity resolution: `github_login`, `orcid`, `domain`, `linkedin_url`)

This uniformity lets us define a single `SourceAdapter` interface and plug in new sources without touching the core ingestion pipeline.

### 2.2 The interface

```java
public interface SourceAdapter {

    /** The source_type string stored in the sources table (e.g. "github", "orcid_pub"). */
    String sourceType();

    /** Fetch raw content from the external system. May throw on network/API errors. */
    RawContent fetch(SourceRef ref) throws IngestionException;

    /** Extract the high-signal core text from raw content (strip boilerplate, noise). */
    String extractHighSignalCore(RawContent raw);

    /** Build the human-readable label for citation chips (e.g. "github.com/{owner}/{repo}"). */
    String buildLabel(RawContent raw);

    /** Extract external identifiers for entity resolution. */
    List<ExternalId> extractExternalIds(RawContent raw);

    /** Derive profile fields for a new entity (name, headline, org, lat/lng).
     *  Only called when no existing entity is found. */
    EntityProfile deriveProfile(RawContent raw);

    /** Derive tags/affiliation/etc. — stubs in Stage 1, activated in later stages. */
    default DerivationResult deriveExtras(RawContent raw) {
        return DerivationResult.empty(); // no-op in Stage 1
    }

    /** For adapters that produce MULTIPLE sources from one input (GitHub repos,
     *  OpenAlex publications), discover the sub-sources. Adapters that produce
     *  exactly one source return a single-element list. */
    default List<SourceRef> discoverSources(SourceRef ref) throws IngestionException {
        return List.of(ref); // default: one source per input (LinkedIn, website, manual)
    }
}
```

```java
// Value objects flowing through the pipeline
public record SourceRef(
    String url,
    String sourceType,
    String provenance,    // "harvested" | "user_supplied" | "self_declared"
    String consentBasis,  // "public_data" | "user_consent" | "self_declared"
    Map<String, String> context  // source-specific params (e.g. {"github_login": "octocat"})
) {}

public record RawContent(
    String text,           // the full raw text/JSON
    Map<String, Object> metadata,  // source-specific structured data
    Instant fetchedAt
) {}

public record ExternalId(String idType, String idValue) {}

// Returned by discoverSources() for multi-source adapters
public record SourcePayload(
    String url,              // source_url (unique per source)
    String coreText,         // high-signal text to chunk
    String label,            // citation chip text
    String section,          // section label for chunks (e.g. "README", "Embedded Systems")
    List<ExternalId> externalIds
) {}

public record EntityProfile(
    String entityType,     // "person" | "organization"
    String displayName,
    String headline,
    String orgName,
    Double latitude,
    Double longitude,
    Map<String, Object> metadata
) {}
```

### 2.3 The ingestion orchestrator (core pipeline)

This is the implementation of the `ingestSource()` algorithm from §4.2 of the reference. It is **source-agnostic** — it delegates all source-specific logic to the adapter:

```java
@Service
public class IngestionOrchestrator {

    private final SourceAdapterRegistry adapterRegistry;
    private final EntityRepository entities;
    private final EntityIdentifierRepository identifiers;
    private final SourceRepository sources;
    private final ChunkRepository chunks;
    private final EmbeddingService embeddingService;
    private final ChunkingService chunkingService;

    public IngestionResult ingest(SourceRef ref) {
        SourceAdapter adapter = adapterRegistry.getAdapter(ref.sourceType());

        // 1. FETCH
        RawContent raw = adapter.fetch(ref);
        String coreText = adapter.extractHighSignalCore(raw);
        String label = adapter.buildLabel(raw);
        String hash = sha256(normalize(coreText));

        // 2. DEDUP — idempotent short-circuit
        Optional<Source> existing = sources.findByUrl(ref.url());
        if (existing.isPresent() && existing.get().getContentHash().equals(hash)) {
            return IngestionResult.skipped(existing.get().getEntityId());
        }

        // 3. RESOLVE IDENTITY (before creating an entity)
        List<ExternalId> ids = adapter.extractExternalIds(raw);
        Optional<Entity> entity = identifiers.findEntityByAnyId(ids);

        Entity resolved;
        if (entity.isEmpty()) {
            // Create new entity with derived profile
            EntityProfile profile = adapter.deriveProfile(raw);
            resolved = entities.create(profile);
            identifiers.insertAll(resolved.getId(), ids);
        } else {
            resolved = entity.get();
            identifiers.insertMissing(resolved.getId(), ids); // converge IDs onto one entity
        }

        // 4. UPSERT SOURCE
        Source source = sources.upsertByUrl(
            resolved.getId(), ref.sourceType(), ref.url(), label,
            ref.provenance(), ref.consentBasis(), raw.text(), hash,
            "ok", Instant.now()
        );

        // 5. RE-CHUNK (delete old chunks for this source, then rebuild)
        chunks.deleteBySourceId(source.getId());
        List<ChunkSegment> segments = chunkingService.recursiveSplit(
            coreText, 400, 60,  // maxTokens=400, overlapTokens=60
            List.of(Boundary.SECTION, Boundary.PARAGRAPH, Boundary.SENTENCE)
        );

        // 6. EMBED + PERSIST CHUNKS
        for (int i = 0; i < segments.size(); i++) {
            ChunkSegment seg = segments.get(i);
            float[] vec = embeddingService.embed("passage: " + seg.text());
            chunks.insert(
                source.getId(), resolved.getId(), i, seg.text(), seg.section(),
                seg.tokenCount(), vec, "multilingual-e5-large", Instant.now()
            );
        }

        // 7. DERIVE (stubs in Stage 1 — activated in later stages)
        DerivationResult derived = adapter.deriveExtras(raw);
        // proposeTags(entity.id, derived.tags())         // Stage 6
        // proposeHub(entity.id, derived.hubCandidate())  // Stage 3
        // setRegionIfKnown(entity.id, derived.region())  // Stage 3
        // setAffiliationIfKnown(entity.id, derived.affiliation()) // Stage 7

        return IngestionResult.ingested(resolved.getId(), source.getId(), segments.size());
    }

    /** For multi-source adapters (GitHub repos, OpenAlex publications):
     *  discover sub-sources → resolve entity ONCE → create N sources + chunks. */
    public List<IngestionResult> ingestMulti(SourceRef ref) {
        SourceAdapter adapter = adapterRegistry.getAdapter(ref.sourceType());
        List<SourceRef> subSources = adapter.discoverSources(ref);

        if (subSources.size() == 1) {
            return List.of(ingest(subSources.get(0)));
        }

        // Resolve entity from the FIRST sub-source (they all share the same external IDs)
        List<IngestionResult> results = new ArrayList<>();
        Entity resolved = null;

        for (SourceRef sub : subSources) {
            // Skip dedup check: each sub-source has a unique URL
            IngestionResult result = ingest(sub);
            results.add(result);
        }
        return results;
    }
}
```

### 2.4 The registry (extensibility mechanism)

```java
@Component
public class SourceAdapterRegistry {
    private final Map<String, SourceAdapter> adapters;

    public SourceAdapterRegistry(List<SourceAdapter> adapterBeans) {
        this.adapters = adapterBeans.stream()
            .collect(toMap(SourceAdapter::sourceType, identity()));
    }

    public SourceAdapter getAdapter(String sourceType) {
        SourceAdapter adapter = adapters.get(sourceType);
        if (adapter == null) {
            throw new IllegalArgumentException("No adapter registered for source_type: " + sourceType);
        }
        return adapter;
    }

    public Set<String> registeredTypes() {
        return adapters.keySet();
    }
}
```

**Adding a new source = implement `SourceAdapter` + annotate as `@Component`.** The registry auto-discovers it via Spring component scanning. Zero changes to the orchestrator.

---

## 3. Per-Source Implementation Details

### 3.1 GitHub Repositories (`source_type = "github"`)

#### What we fetch

The GitHub REST API (authenticated, 5,000 req/hour with a Personal Access Token):

| API Call | Endpoint | What we extract |
|---|---|---|
| User profile | `GET /users/{username}` | name, bio, company, location, blog, hireable, avatar, followers, public_repos |
| Repos list | `GET /search/repositories?q=user:{username}+fork:false&sort=stars&order=desc&per_page=15` | top 15 repos (sorted by stars, forks excluded), each: name, description, language, topics, stargazers_count, pushed_at |
| README (per repo) | `GET /repos/{owner}/{repo}/readme` (accept: `application/vnd.github.raw`) | the README content for each top repo (stripped of badges/HTML) |

**Why the Search API instead of `/users/{username}/repos`:** The `/users/{username}/repos` endpoint only supports `sort=created|updated|pushed|full_name` — it **cannot sort by stars**. The Search API (`/search/repositories`) supports `sort=stars&order=desc`, so we get the top 15 by stars in a single call. The `fork:false` filter excludes forked repos (we want original work, not copies).

**Rate limit handling:** Respect `X-RateLimit-Remaining` and `X-RateLimit-Reset` headers. When remaining ≤ 0, pause until reset. Use a GitHub PAT stored in application config (`orbit.github.token`). 1 user lookup + 1 search + 15 READMEs = 17 API calls per entity (well within 5k/hr). Repos with no README use just description + topics as core text.

#### High-signal core text construction

**Per-repo core text** (each repo = one source, one core text):

```
Repo: {repo_name}
Description: {description}
Language: {language}
Topics: {topics}
Stars: {stars}
Last Pushed: {pushed_at}

{readme_text_stripped_of_badges_and_html}
```

The profile bio (`name`, `bio`, `company`, `location`) is **not** a source — it's used only for entity creation (`deriveProfile()`). The repos ARE the searchable content.

**Boilerplate stripping for READMEs:** Remove badge images (`![...](https://img.shields.io/...)`), HTML comments, license headers, CI status badges, and `<div align="center">` wrappers. Keep section headings, paragraphs, and lists. The goal is the *narrative* — what the project does, why it exists, how it works — not the marketing chrome.

**Chunking within a repo:** The README is split by `##` heading boundaries. Each section (Installation, Features, Testing, etc.) becomes its own chunk. A short README (≤400 tokens) is one chunk. The `section` field on each chunk records the heading it came from (e.g., `"Features"`, `"Installation"`). Repos with no README produce a single chunk from `description + topics` with `section = "Description"`.

**Why per-repo, not profile-level:** Different repos capture different expertise areas (embedded systems vs. web development). Per-repo sources give us:
- **Source-level sharpness:** each repo is its own source → citation chip is `github.com/{owner}/{repo}` (specific)
- **Chunk-level sharpness:** each README section is its own chunk → focused vectors
- A person with two very different repos is findable for **both** expertise areas independently

#### External IDs

| id_type | id_value | Notes |
|---|---|---|
| `github_login` | `{username}` | Primary identity key |

If the user's GitHub profile links to a personal website (`blog` field), that URL can be passed as a secondary ingestion target (personal_site adapter).

#### Label

`github.com/{owner}/{repo}` — one source per repo (up to 15 repos, sorted by stars). The profile bio is entity metadata, not a source.

#### Derive profile

```
entityType: "person"
displayName: {name} or {username} (fallback)
headline: {bio} (first 120 chars) or "Developer at {company}"
orgName: {company} (if present)
latitude/longitude: geocode from {location} (optional in Stage 1)
```

#### Derive extras (for later stages)

- **Tags (Stage 6):** from repo `topics[]` and `language` fields across all repos. Aggregate by frequency, take top 15.
- **Hub candidate (Stage 3):** from `company` field if it matches a known organization/institution.
- **Affiliation (Stage 7):** if `company` is set, suggest `affiliation_kind = self_employed` or link to the company entity.

#### Design decision: per-repo sources

**Decision: Per-repo sources (one source per repo, up to 15).** The reference doc §4.1 specifies `label = github.com/{owner}/{repo}` and `fetch = /repos/{owner}/{repo}` + `/readme` — this is per-repo, not per-profile. Each repo becomes its own source with its own citation chip. All repos share the same entity (resolved via `github_login`). The user profile (`GET /users/{username}`) is fetched once for entity creation and external ID extraction, not as a source.

**Adapter mechanism:** `GithubAdapter.discoverSources()` returns one `SourceRef` per repo (up to 15, sorted by stars). The orchestrator's `ingestMulti()` resolves the entity once, then creates N sources + chunks.

---

### 3.2 LinkedIn Profile PDF (`source_type = "linkedin_pdf"`)

#### What we fetch

The user **uploads** a PDF export of their LinkedIn profile (LinkedIn → "More" → "Save to PDF"). This is a user-supplied source, not an API call — `provenance = "user_supplied"`, `consent_basis = "user_consent"`.

LinkedIn PDFs have a **consistent structure** with section headers in a predictable order:

```
{Name}
{Headline/Title}
{Location}
{Industry}

{Email}
{Phone}
{LinkedIn URL}
{Websites}

Top Skills
{skill1}
{skill2}
...

Summary
{summary text}

Experience
{Job Title}
{Company}
{Dates}
{Location}
{Description bullet points}

Education
{School}
{Degree}
{Field}
{Dates}

Languages
{Language} ({proficiency})

Skills & Expertise
{comma-separated skill list}
```

#### PDF text extraction (Java ecosystem)

Use **Apache PDFBox** (already used in the reference doc's Java/Spring Boot stack):

```java
@Component
public class LinkedInPdfAdapter implements SourceAdapter {

    @Override
    public RawContent fetch(SourceRef ref) {
        // ref.context() contains the uploaded PDF bytes or file path
        byte[] pdfBytes = ref.context().get("pdf_bytes");
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String fullText = stripper.getText(doc);
            return new RawContent(fullText, Map.of(), Instant.now());
        }
    }
}
```

**Section-aware parsing:** After raw text extraction, parse the LinkedIn section headers to build a structured representation:

```java
public class LinkedInPdfParser {
    // LinkedIn PDF section headers (stable across profiles)
    private static final List<String> SECTIONS = List.of(
        "Top Skills", "Summary", "Experience", "Education",
        "Languages", "Skills & Expertise", "Volunteer Experience",
        "Certifications", "Publications", "Projects",
        "Honors & Awards", "Courses"
    );

    public ParsedLinkedInProfile parse(String rawText) {
        // Split on section headers, extract structured fields
        // Returns: name, headline, location, email, linkedinUrl,
        //          summary, experience[], education[], skills[], languages[]
    }
}
```

#### High-signal core text construction

```
=== LINKEDIN PROFILE: {name} ===
Headline: {headline}
Location: {location}

--- SUMMARY ---
{summary text}

--- EXPERIENCE ---
{job_title} at {company} ({dates})
{description}

[repeat for each role]

--- EDUCATION ---
{degree} in {field} at {school} ({dates})

--- TOP SKILLS ---
{skill1}, {skill2}, {skill3}, ...

--- LANGUAGES ---
{language} ({proficiency})
```

**What we DON'T embed:** Contact info (email, phone — PII, not useful for retrieval), the "People Also Viewed" section, recommendations (noise), certifications (low signal for discovery). We DO keep contact info in `metadata` for later use (contact handoff, Stage 8) but don't embed it.

#### External IDs

| id_type | id_value | Notes |
|---|---|---|
| `linkedin_url` | `linkedin.com/in/{vanity-name}` | Parsed from the PDF's URL line |

If the LinkedIn URL is not present in the PDF, we have no external ID from this source alone — entity resolution relies on cross-matching with other sources (e.g., name + company match).

#### Label

`"LinkedIn-Profil"` (as specified in the reference doc §4.1)

#### Derive profile

```
entityType: "person"
displayName: {name}
headline: {headline}
orgName: {current_company} (from most recent Experience entry)
latitude/longitude: geocode from {location} (optional)
```

#### Derive extras (for later stages)

- **Tags (Stage 6):** from Top Skills + Skills & Expertise sections. These are already curated by the user — high quality.
- **Affiliation (Stage 7):** from current Experience entry → `affiliation_kind` (company/self_employed) + `entity_links` (person↔org).
- **Languages:** from Languages section → `entities.languages[]` (Stage 5).
- **Hub candidate (Stage 3):** from current employer matching a known organization.

#### Privacy & consent

LinkedIn PDFs are **user-uploaded** — the user explicitly provides their own data. `provenance = "user_supplied"`, `consent_basis = "user_consent"`. The raw PDF bytes should NOT be stored in `sources.raw_content` (too large, PII-heavy). Instead, store the **parsed text** (the structured sections) as `raw_content`, and discard the original PDF bytes after extraction.

---

### 3.3 ORCID via OpenAlex (`source_type = "orcid_pub"`)

#### What we fetch

A two-step process using the **OpenAlex API** (free, no auth required but polite with `mailto` in User-Agent; API key recommended for higher rate limits):

**Step 1: Resolve ORCID → OpenAlex Author**

```
GET https://api.openalex.org/authors/https://orcid.org/{orcid}
  ?select=id,display_name,affiliations,last_known_institutions,summary_stats,works_count
```

Returns the OpenAlex author entity with their OpenAlex ID, display name, affiliations, and work count.

**Step 2: Fetch works by this author**

```
GET https://api.openalex.org/works?filter=author.id:{openalex_author_id}
  &sort=-cited_by_count
  &per_page=25
  &select=id,title,publication_year,abstract_inverted_index,primary_topic,cited_by_count,doi,type
```

Returns the author's top-cited works (sorted by citation count). Pagination via cursor (`cursor=*` → `meta.next_cursor`).

**Abstract reconstruction:** OpenAlex stores abstracts as inverted indices (`abstract_inverted_index`). Reconstruct the plain-text abstract:

```java
public String reconstructAbstract(Map<Integer, List<Integer>> invertedIndex) {
    if (invertedIndex == null || invertedIndex.isEmpty()) return "";
    int maxPos = invertedIndex.values().stream()
        .flatMap(List::stream).max(Integer::compareTo).orElse(0) + 1;
    String[] words = new String[maxPos];
    invertedIndex.forEach((word, positions) ->
        positions.forEach(pos -> words[pos] = word));
    return String.join(" ", words);
}
```

#### High-signal core text construction

**Per-work core text** (each publication = one source, one core text):

```
{primary_topic.display_name}

{title}

{reconstructed_abstract}
```

The `primary_topic.display_name` (e.g., "Embedded Systems", "Machine Learning & Artificial Intelligence") prefixes the text for context and is also stored as the chunk's `section` field. `Type` and `Citations` are metadata, not embedded text — they go in `sources.metadata`.

**Design decision: per-work sources.** The reference doc §4.1 specifies `label = "«{title}» ({year})"` and `source_url` per work — this is per-publication, not per-author. Each publication becomes its own source row. This gives us per-publication citation chips, which are much more useful for grounding ("«HIL-Testing at Scale» (Embedded World 2025)") than a single "ORCID Publications" chip. The OpenAlex adapter creates one source per work:

```
For each work:
  source_url = https://openalex.org/{work_id}
  label = "«{title}» ({year})"
  coreText = primary_topic + title + abstract
  chunkSection = primary_topic.display_name
  externalIds = [{orcid, {orcid_value}}, {doi, {doi_value}}]
```

**Chunking:** Most publications are **one chunk** (title + abstract ≤400 tokens). If an abstract is unusually long (>400 tokens), it splits on sentence boundaries — but this is rare. The `section` field on the chunk records `primary_topic.display_name`.

All works share the same `entity_id` (resolved via ORCID). The `source_url` (OpenAlex work ID) is the idempotency key.


#### External IDs

| id_type | id_value | Notes |
|---|---|---|
| `orcid` | `0000-0000-0000-0000` | Primary identity key — passed as input |
| `doi` | `10.xxxx/xxxx` | Per-work, for citation linking |

#### Rate limits & politeness

OpenAlex is free but rate-limited. Best practices:
- Include `mailto=your@email.com` in the User-Agent header for the **polite pool** (faster responses, higher rate limits). No API key needed for Stage 1.
- API calls per entity: 1 author lookup + 1 works list (with cursor pagination if >25 works). ~2-3 calls per entity.
- Implement exponential backoff on HTTP 429.

#### Derive profile

```
entityType: "person"
displayName: {author.display_name}
headline: "Researcher" or derived from affiliations
orgName: {last_known_institution.display_name} (if present)
latitude/longitude: from institution's geo location (if available)
```

#### Derive extras (for later stages)

- **Tags (Stage 6):** from `primary_topic` fields across all works. The OpenAlex topic hierarchy (Domain → Field → Subfield → Topic) gives us structured tags: e.g., `{Artificial Intelligence, Natural Language Processing, Computer Science}`.
- **Hub candidate (Stage 3):** from `last_known_institutions[0]` — if the institution matches a known Swiss university/FHNW, suggest that hub.
- **Affiliation (Stage 7):** from `affiliations[]` → academic affiliation kind + `entity_links` to the institution.

---

### 3.4 Personal Website (`source_type = "personal_site"`)

#### What we fetch

A polite crawl of the user's personal website, starting from the root URL. The crawl follows internal links to discover high-value pages:

```
1. Start at {url} (e.g., https://johndoe.dev)
2. Fetch the page HTML
3. Extract main content (boilerplate stripping)
4. Discover internal links (same domain)
5. Prioritize pages matching: /about, /projects, /bio, /cv, /resume, /work, /research
6. Crawl up to N=5 high-value pages + the homepage
7. For each page: extract main text, store as part of the same source
```

**Boilerplate stripping:** This is the critical quality step. The reference doc §4.3 mandates: *"strip boilerplate before embedding — cookie banners, nav, badge images, license headers — embedding noise degrades retrieval more than missing text does."*

Since Orbit is a **Java Spring Boot** monolith, we need a Java-compatible solution. Options:

| Approach | Pros | Cons |
|---|---|---|
| **Jsoup + custom rules** | Pure Java, no external deps, fine control | Must maintain boilerplate rules manually |
| **Boilerpipe (Java)** | Purpose-built boilerplate removal, well-tested | Less actively maintained, but stable |
| **Call a Python microservice (Trafilatura)** | Best-in-class extraction, actively maintained | Adds a service dependency |
| **Mozilla Readability port (readability4j)** | Good quality, Java-native | Less battle-tested than Trafilatura |

**Recommendation: Jsoup + custom rules for Stage 1, with a clear path to Trafilatura as a sidecar if quality is insufficient.** Jsoup gives us DOM manipulation, CSS selectors, and text extraction — all we need for the initial boilerplate removal. The rules are straightforward:

```java
public class WebsiteContentExtractor {

    public String extract(String html) {
        Document doc = Jsoup.parse(html);

        // 1. Remove known boilerplate containers
        doc.select("nav, header, footer, aside, script, style, noscript, " +
                   "[class*='nav'], [class*='menu'], [class*='sidebar'], " +
                   "[class*='footer'], [class*='cookie'], [class*='banner'], " +
                   "[class*='popup'], [class*='ad-'], [id*='cookie'], " +
                   "[role='navigation'], [role='banner'], [role='contentinfo']")
           .remove();

        // 2. Find the main content container
        Element main = doc.selectFirst("main, article, [role='main'], " +
                                       "[class*='content'], [class*='post'], " +
                                       "[class*='about'], [class*='bio']");
        Element target = (main != null) ? main : doc.body();

        // 3. Extract text with structure preservation
        String text = target.text();  // Jsoup's text() strips tags, joins inline
        return cleanWhitespace(text);
    }
}
```

**JS-rendered sites:** Many modern personal sites are SPAs (React/Next.js). Static Jsoup parsing will return empty content for these. Strategy:

1. Try static fetch first (fast, works for 80%+ of personal sites).
2. If the extracted text is < 200 chars (suspiciously short), flag as `fetch_status = "js_required"` and skip (or queue for headless browser rendering in a future enhancement).

#### High-signal core text construction

```
=== PERSONAL WEBSITE: {domain} ===
Title: {page_title}

--- HOMEPAGE ---
{homepage_main_text}

--- ABOUT ---
{about_page_text}

--- PROJECTS ---
{projects_page_text}

[repeat for each crawled page]
```

Each page's text is a natural chunking boundary — the `section` field in chunks records which page it came from.

#### External IDs

| id_type | id_value | Notes |
|---|---|---|
| `domain` | `johndoe.dev` | Extracted from the URL, normalized (strip www., https://) |

The domain is a **registrable** identifier — it can be used to match across sources (e.g., a GitHub profile's `blog` field pointing to the same domain).

#### Label

`{domain}` (e.g., `johndoe.dev`)

#### Derive profile

Personal websites alone rarely give us enough for a full entity profile. We extract what we can:
```
entityType: "person" (default; could be "organization" if the site is clearly a company)
displayName: {page_title} or {og:site_name} or {domain}
headline: extracted from <h1> or <title>
```
If the personal website is the first source for an entity, the profile is minimal — it gets enriched when other sources (GitHub, LinkedIn, ORCID) are ingested.

#### Crawl configuration

| Parameter | Value | Rationale |
|---|---|---|
| Max pages | 5 | Focus on high-value pages, avoid deep crawls |
| Max depth | 2 | Homepage → section pages only |
| Priority paths | `/about`, `/projects`, `/bio`, `/cv`, `/resume`, `/work`, `/research` | These carry the most identity/skill signal |
| Politeness delay | 1 second between requests | Respect the site's server |
| User-Agent | `Orbit-Discovery/1.0 (mailto:contact@orbit.ch)` | Identify ourselves, include contact |
| robots.txt | Respect `Disallow` rules | Ethical crawling |

---

## 4. Entity Resolution Strategy

### 4.1 The problem

The same person may provide a GitHub profile, a LinkedIn PDF, an ORCID iD, and a personal website. These four sources must all land on **one entity row**. The resolution mechanism is `entity_identifiers` — each source extracts external IDs, and `findEntityByAnyId(ids)` checks whether any of those IDs already exist.

### 4.2 Resolution flow

```
Source A (GitHub) → ids: [github_login:octocat]
  → No match → Create entity #1 → Store identifier (github_login:octocat)

Source B (LinkedIn PDF) → ids: [linkedin_url:linkedin.com/in/octocat]
  → No match (linkedin_url not in DB yet) → Create entity #2 → Store identifier
  ⚠️ PROBLEM: Two entities for the same person!

Source C (ORCID) → ids: [orcid:0000-0001-..., doi:10.xxx/...]
  → No match → Create entity #3 → Store identifiers
  ⚠️ Three entities now!
```

### 4.3 Solution: Multi-signal matching

The `findEntityByAnyId` lookup handles **direct ID matches**. But when different sources provide different ID types (GitHub login vs. LinkedIn URL vs. ORCID), we need **cross-source matching**:

**Strategy 1: Shared identifiers across sources**

Some sources naturally provide the same identifier:
- GitHub profile `blog` field → domain (matches personal_site's `domain` ID)
- LinkedIn PDF may contain a personal website URL → domain (matches personal_site)
- ORCID profile may contain a personal website URL → domain (matches personal_site)

**Strategy 2: Profile field matching (fallback)**

When no shared external ID exists, match on profile fields:
- **Name + organization** — if two entities have the same `display_name` AND the same `org_name`, they're likely the same person.
- **Name + location** — same `display_name` AND geographically close.
- **Domain cross-reference** — GitHub profile mentions a website → that website is a personal_site source for the same entity.

**Implementation: Enhanced `findEntityByAnyId`**

```java
public Optional<Entity> findEntityByAnyId(List<ExternalId> ids) {
    // 1. Direct ID match
    for (ExternalId id : ids) {
        Optional<Entity> match = identifiers.findByTypeAndValue(id.idType(), id.idValue());
        if (match.isPresent()) return match;
    }

    // 2. Cross-source domain match
    // If any of the IDs is a 'domain', check if another entity has a source
    // whose URL references that domain
    Optional<ExternalId> domainId = ids.stream()
        .filter(id -> id.idType().equals("domain")).findFirst();
    if (domainId.isPresent()) {
        Optional<Entity> match = sources.findEntityByUrlContainingDomain(domainId.get().idValue());
        if (match.isPresent()) return match;
    }

    // 3. Name + org fuzzy match (last resort, requires human confirmation)
    // This is logged but NOT auto-merged in Stage 1 — it's a Stage 9 concern.
    // In Stage 1, we create a new entity and log a potential-merge candidate.

    return Optional.empty();
}
```

### 4.4 The onboarding advantage

In the **onboarding flow** (user-initiated), entity resolution is much simpler because the user provides multiple sources at once and can confirm identity. The user adds sources sequentially, and each subsequent source is offered as "we found an existing profile for you — is this you?" rather than silently creating a duplicate.

### 4.5 Merge handling (Stage 9 concern, logged in Stage 1)

When `findEntityByAnyId` discovers that IDs from a new source resolve to **different existing entities**, that's a merge case. Stage 1 logs this:

```sql
INSERT INTO ingestion_log (event_type, details, created_at)
VALUES ('potential_entity_merge',
  jsonb_build_object('new_source_url', ?, 'conflicting_entity_ids', ?::bigint[]),
  now());
```

Proper entity merge (consolidating chunks, sources, identifiers) is deferred to Stage 9 per the reference doc.

---

## 5. Onboarding Flow (User Experience)

### 5.1 The onboarding journey

The user-facing onboarding flow is designed for a **flawless experience** — minimal friction, maximum content capture, with the user in control of what goes into their profile.

```
┌─────────────────────────────────────────────────────────────┐
│                    ONBOARDING FLOW                           │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  1. WELCOME                                                  │
│     "Erstelle dein Profil — verbinde deine Quellen"         │
│     [Start]                                                  │
│                                                              │
│  2. SOURCE COLLECTION (repeatable)                          │
│     ┌───────────────────────────────────────────────┐       │
│     │  What describes you best?                      │       │
│     │  [GitHub] [LinkedIn PDF] [ORCID] [Website]    │       │
│     │  [+ Add more later]                            │       │
│     └───────────────────────────────────────────────┘       │
│                                                              │
│  3. PER-SOURCE INPUT                                         │
│     GitHub → Enter username → Validate → Fetch              │
│     LinkedIn → Upload PDF → Parse → Extract                 │
│     ORCID → Enter ORCID iD → Validate → Fetch              │
│     Website → Enter URL → Validate → Crawl                  │
│                                                              │
│  4. INGESTION (async, per source)                           │
│     "Importing your GitHub profile... ✓ Done"               │
│     "Importing your LinkedIn profile... ✓ Done"             │
│     "Importing your publications... ✓ Done (12 works)"      │
│     "Importing your website... ✓ Done (4 pages)"            │
│                                                              │
│  5. PROFILE PREVIEW                                         │
│     ┌───────────────────────────────────────────────┐       │
│     │  Name: John Doe                                │       │
│     │  Headline: Senior Software Engineer             │       │
│     │  Org: Acme Corp                                │       │
│     │                                                  │       │
│     │  Sources (4):                                   │       │
│     │  ✓ github.com/johndoe (15 repos)               │       │
│     │  ✓ LinkedIn-Profil                              │       │
│     │  ✓ «Machine Learning at Scale» (2024)          │       │
│     │  ✓ johndoe.dev (4 pages)                        │       │
│     │                                                  │       │
│     │  Derived tags: Python, ML, DevOps, Cloud       │       │
│     │  [Edit tags] (Stage 6)                          │       │
│     │                                                  │       │
│     │  [Looks good — Create profile]                  │       │
│     └───────────────────────────────────────────────┘       │
│                                                              │
│  6. CONFIRMATION                                            │
│     "Dein Profil ist erstellt und durchsuchbar."            │
│     "You can add more sources anytime."                      │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 5.2 Input validation (per source)

| Source | Validation | Error handling |
|---|---|---|
| GitHub | Check `GET /users/{username}` returns 200 | "GitHub user not found — check the username" |
| LinkedIn PDF | Check file is a valid PDF, ≤ 5MB | "Invalid PDF — re-export from LinkedIn" |
| ORCID | Check ORCID checksum (modulo-11), validate via OpenAlex | "Invalid ORCID iD — verify at orcid.org" |
| Website | Check URL resolves (HTTP 200), domain is reachable | "Website not reachable — check the URL" |

### 5.3 Async ingestion with progress feedback

Ingestion is **async** (each source is a job in a queue). The onboarding UI polls for completion:

```java
// POST /api/onboarding/sources
{
  "source_type": "github",
  "context": {"github_login": "octocat"},
  "provenance": "user_supplied",
  "consent_basis": "user_consent"
}

// Response 202 Accepted
{
  "ingestion_job_id": "uuid-...",
  "status": "queued",
  "poll_url": "/api/onboarding/jobs/uuid-..."
}

// GET /api/onboarding/jobs/uuid-...
{
  "status": "completed",  // queued | fetching | extracting | embedding | completed | error
  "result": {
    "entity_id": 42,
    "source_id": 17,
    "chunks_created": 12,
    "label": "github.com/octocat"
  }
}
```

### 5.4 Adding sources later (post-onboarding)

After initial onboarding, the user can add more sources anytime:

```
POST /api/profile/sources
{
  "source_type": "orcid_pub",
  "context": {"orcid": "0000-0001-2345-6789"},
  "provenance": "user_supplied",
  "consent_basis": "user_consent"
}
```

This triggers the same ingestion pipeline, with entity resolution linking the new source to the user's existing entity.

---

## 6. Quality Assurance Pipeline

### 6.1 Pre-embedding quality gates

Before any text enters the chunking + embedding stage, it passes through quality checks:

```java
public class ContentQualityFilter {

    public QualityResult check(String coreText, String sourceType) {
        // 1. Minimum content length
        if (coreText == null || coreText.trim().length() < 50) {
            return QualityResult.reject("too_short");
        }

        // 2. Excessive repetition (boilerplate that slipped through)
        String[] lines = coreText.split("\n");
        if (lines.length > 5) {
            double uniqueRatio = (double) new HashSet<>(Arrays.asList(lines)).size() / lines.length;
            if (uniqueRatio < 0.5) {
                return QualityResult.reject("too_repetitive");
            }
        }

        // 3. Encoding artifacts (replacement character density)
        long replacementChars = coreText.chars().filter(c -> c == 0xFFFD).count();
        if ((double) replacementChars / coreText.length() > 0.02) {
            return QualityResult.reject("encoding_errors");
        }

        // 4. Content-to-whitespace ratio
        long nonWhitespace = coreText.chars().filter(c -> !Character.isWhitespace(c)).count();
        if ((double) nonWhitespace / coreText.length() < 0.4) {
            return QualityResult.reject("excessive_whitespace");
        }

        return QualityResult.accept();
    }
}
```

### 6.2 Chunking quality

Per the reference doc §4.3:
- `maxTokens = 400`, `overlapTokens = 60` (~15%)
- Split on the largest boundary that fits: section → paragraph → sentence
- Use the real e5 tokenizer (HF `intintfloat/multilingual-e5-large` via DJL) for token counting — German compounds make chars/4 badly wrong
- Store `token_count`, assert `< 512`
- Keep `section` field for filtering / optional prepend

### 6.3 Embedding quality

- **Prefix is mandatory:** `"passage: " + text` for stored passages, `"query: " + text` for query side
- **Content stored WITHOUT the prefix** — the prefix is prepended at embed time only
- **Batch embeddings** where supported (local Ollama or LM Studio serving `multilingual-e5-large`)
- **One vector per chunk** — no averaging or pooling

### 6.4 Source-level quality scoring

Each source gets a quality indicator in `metadata`:

```json
{
  "quality_signals": {
    "content_length": 4500,
    "chunk_count": 12,
    "avg_chunk_tokens": 380,
    "has_structured_data": true,
    "language_detected": "de",
    "extraction_warnings": []
  }
}
```

This lets us later filter or weight sources by quality (e.g., a GitHub profile with only 2 repos and no READMEs is lower quality than one with 15 repos and detailed READMEs).

---

## 7. Extensibility: Adding New Sources

### 7.1 The recipe

To add a new source type (e.g., `zefix`, `company_site`, `google_scholar`, `researchgate`):

1. **Create a new `SourceAdapter` implementation:**
   ```java
   @Component
   public class ZefixAdapter implements SourceAdapter {
       @Override public String sourceType() { return "zefix"; }
       @Override public RawContent fetch(SourceRef ref) { /* Zefix API call */ }
       @Override public String extractHighSignalCore(RawContent raw) { /* extract Zweck */ }
       @Override public String buildLabel(RawContent raw) { /* company legal name */ }
       @Override public List<ExternalId> extractExternalIds(RawContent raw) { /* zefix_uid */ }
       @Override public EntityProfile deriveProfile(RawContent raw) { /* ... */ }
   }
   ```

2. **That's it.** The Spring component scanner picks it up, the `SourceAdapterRegistry` registers it, and the `IngestionOrchestrator` uses it automatically when `sourceType = "zefix"` is passed.

3. **No changes needed to:**
   - The ingestion orchestrator
   - The chunking service
   - The embedding service
   - The entity resolution logic
   - The DB schema (the `sources.source_type` is a free-text field)
   - The onboarding UI (if the source type is added to the source picker)

### 7.2 Source types already anticipated in the reference doc

| `source_type` | Status | Adapter |
|---|---|---|
| `github` | ✅ This plan | `GithubAdapter` (per-repo) |
| `linkedin_pdf` | ✅ This plan | `LinkedInPdfAdapter` |
| `orcid_pub` | ✅ This plan | `OpenAlexOrcidAdapter` (per-work) |
| `personal_site` | ✅ This plan | `PersonalWebsiteAdapter` |
| `zefix` | Future (Stage 3) | `ZefixAdapter` |
| `company_site` | Future | `CompanyWebsiteAdapter` |
| `manual_text` | Future (Demand min profile) | `ManualTextAdapter` |

### 7.3 Extending the onboarding UI

The source picker in onboarding is driven by the registry:

```java
// GET /api/onboarding/source-types
// Returns the registered adapter types with metadata for the UI
[
  {"type": "github", "label": "GitHub", "icon": "github", "input_type": "text", "placeholder": "GitHub username"},
  {"type": "linkedin_pdf", "label": "LinkedIn", "icon": "linkedin", "input_type": "file", "accept": ".pdf"},
  {"type": "orcid_pub", "label": "ORCID", "icon": "orcid", "input_type": "text", "placeholder": "0000-0000-0000-0000"},
  {"type": "personal_site", "label": "Website", "icon": "globe", "input_type": "url", "placeholder": "https://..."}
]
```

New adapters can provide this metadata via an optional interface method:

```java
default SourceTypeMetadata uiMetadata() {
    return new SourceTypeMetadata(sourceType(), sourceType(), "text", "");
}
```

---

## 8. Build Order (1-Day Session)

We build this together in a single session. The order is load-bearing — each step validates the one before it.

### Step 1 — DB & JPA Foundation
- [ ] Flyway migration: `V1__create_entities_sources_chunks.sql` (Stage 1 schema)
- [ ] JPA entities: `Entity`, `EntityIdentifier`, `Source`, `Chunk`
- [ ] Repositories: basic CRUD + `findByUrl`, `findEntityByAnyId`, `deleteBySourceId`

### Step 2 — Core Pipeline (source-agnostic)
- [ ] `SourceAdapter` interface + value objects (`SourceRef`, `RawContent`, `ExternalId`, `EntityProfile`)
- [ ] `SourceAdapterRegistry` (Spring component scanning)
- [ ] `ChunkingService` (recursive split, 400 tokens / 60 overlap, e5 tokenizer via DJL)
- [ ] `EmbeddingService` (Ollama/LM Studio → `multilingual-e5-large`, `"passage: "` prefix)
- [ ] `IngestionOrchestrator` (the full `ingestSource()` algorithm)
- [ ] `ContentQualityFilter` (pre-embedding gates)

### Step 3 — Adapters (all four)
- [ ] `GithubAdapter` (REST API: user profile → entity, per-repo sources + READMEs)
- [ ] `LinkedInPdfAdapter` (PDFBox + section parser)
- [ ] `OpenAlexOrcidAdapter` (OpenAlex API + abstract reconstruction, per-work sources)
- [ ] `PersonalWebsiteAdapter` (Jsoup crawl + boilerplate stripping)

### Step 4 — Entity Resolution
- [ ] `findEntityByAnyId` with cross-source domain matching
- [ ] ID convergence: `insertMissing` for existing entities
- [ ] Merge-candidate logging

### Step 5 — Onboarding REST API
- [ ] `POST /api/onboarding/sources` (accept source ref, queue ingestion)
- [ ] `GET /api/onboarding/jobs/{id}` (poll status)
- [ ] `GET /api/onboarding/source-types` (registry-driven source picker)
- [ ] Input validation per source type

### Step 6 — Verify End-to-End
- [ ] Ingest one person from GitHub + LinkedIn PDF + ORCID + website → verify convergence to one entity
- [ ] Re-ingest same source → verify `content_hash` short-circuit (SKIPPED)
- [ ] Inspect chunks: check token counts < 512, embeddings populated, `section` labels correct

---

## 9. Technical Considerations

### 9.1 Embedding infrastructure

- **Model:** `multilingual-e5-large` (1024 dims) — served locally via Ollama or LM Studio
- **Batch size:** 32–64 chunks per batch (depending on available VRAM)
- **Prefix:** `"passage: "` for stored chunks, `"query: "` for query-side (at search time)
- **Storage:** `vector(1024)` in PostgreSQL + pgvector with HNSW index (`vector_ip_ops`)

### 9.2 Rate limits per source

| Source | Limit | Strategy |
|---|---|---|
| GitHub REST API | 5,000 req/hour (authenticated) | PAT in config; respect `X-RateLimit-Reset` |
| OpenAlex API | ~100k req/day (with mailto) | Exponential backoff on 429; batch work IDs with `\|` operator |
| LinkedIn PDF | N/A (user upload) | File size limit 5MB; parse synchronously |
| Personal website | Site-dependent | 1s delay between requests; respect robots.txt |

### 9.3 Error handling matrix

| Error | Behavior | User-facing message |
|---|---|---|
| Network timeout (fetch) | `sources.fetch_status = "error"`, keep old chunks | "Could not fetch {source} — will retry later" |
| Invalid input (bad username, bad ORCID) | Reject at validation, no DB writes | "Invalid {field} — please check and try again" |
| PDF parse failure | `sources.fetch_status = "error"`, no entity created | "Could not read your LinkedIn PDF — try re-exporting" |
| Website unreachable / JS-only | `sources.fetch_status = "js_required"`, no chunks | "Your website uses JavaScript rendering — content will be added in a future update" |
| Embedding service down | Queue chunk for retry, don't persist partial | "Importing in background — we'll notify you when done" |
| Entity merge detected | Log potential merge, attach to first entity | (silent — surfaced to admin dashboard) |

### 9.4 Database sizing estimates

| Entity | Estimate | Chunks per source | Total chunks |
|---|---|---|---|
| GitHub profile | ~10–15 chunks (bio + 10 READMEs) | 10–15 | |
| LinkedIn PDF | ~8–12 chunks (summary + 5 roles + education + skills) | 8–12 | |
| ORCID publications | ~1 chunk per work × 25 works | 25 | |
| Personal website | ~5–10 chunks (5 pages, 1–2 chunks each) | 5–10 | |
| **Total per person** | | | **~48–62 chunks** |

At 1024 dims × 4 bytes = 4KB per vector, ~60 chunks = ~240KB of vector data per person. For 10,000 entities: ~2.4GB of vector data — well within PostgreSQL + pgvector capabilities.

### 9.5 Consent & provenance

Every source carries provenance and consent basis metadata:

| Source | provenance | consent_basis |
|---|---|---|
| GitHub | `harvested` (public API) | `public_data` |
| LinkedIn PDF | `user_supplied` (user upload) | `user_consent` |
| ORCID | `harvested` (public API) | `public_data` |
| Personal website | `harvested` (public web) | `public_data` |

For harvested sources, the user confirms during onboarding that this is their data and they consent to its use. For user-supplied sources, consent is implicit in the upload.

---

## 10. What's NOT in This Plan (Later Stages)

| Feature | Stage | Notes |
|---|---|---|
| Tags (Kompetenzen) derivation | Stage 6 | `proposeTags()` stub returns empty in Stage 1 |
| Hub/region assignment | Stage 3 | `proposeHub()` / `setRegionIfKnown()` stubs |
| Affiliation & Person↔Org links | Stage 7 | `setAffiliationIfKnown()` stub |
| Availability (date range, Pensum) | Stage 5 | Manual user input, not derived from sources |
| Entity merge | Stage 9 | Stage 1 logs merge candidates only |
| Hybrid search function | Stage 2 | `hybrid_search` arrives after chunks exist |
| Scoped search (Hub→Region→CH) | Stage 3 | Needs geo tables |
| Re-ranking (soft signals) | Stage 4+ | Needs availability, presence, proximity |
| Grounded generation (Gemini) | Stage 4 | Needs the search pipeline |

This plan delivers the **content substrate** — the chunks that every downstream stage retrieves, ranks, grounds on, and cites. Getting this right is the foundation for everything else.

---

## 11. Key Design Decisions Summary

| Decision | Choice | Rationale |
|---|---|---|
| **Adapter pattern** | Strategy via `SourceAdapter` interface | New sources = one new class, zero core changes |
| **GitHub: per-repo sources** | One source per repo (up to 15, sorted by stars) | Reference doc §4.1 says per-repo. Each expertise area independently findable. Citation chip = `github.com/{owner}/{repo}`. |
| **ORCID: per-work sources** | One source per publication | Per-publication citation chips ("«Title» (2024)") are much better for grounding |
| **LinkedIn: store parsed text, not PDF** | Parsed structured text in `raw_content` | PII safety, smaller storage, already-extracted sections |
| **Website: Jsoup + rules** | Pure Java boilerplate removal | No external service dependency. Upgrade to Trafilatura sidecar if quality insufficient. |
| **Entity resolution: IDs first, fields fallback** | `findEntityByAnyId` → domain cross-match → name+org (logged, not auto-merged) | Conservative; avoids false merges in Stage 1 |
| **Async ingestion** | Job queue with polling | User doesn't wait for embeddings; progress feedback |
| **Quality gates pre-embedding** | Length, repetition, encoding, whitespace checks | Bad text never becomes a bad chunk |
| **JS-rendered sites** | Static fetch first, flag as `js_required` if too short | Most personal sites are static; headless browser is a future enhancement |