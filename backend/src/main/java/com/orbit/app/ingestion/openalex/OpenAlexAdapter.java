package com.orbit.app.ingestion.openalex;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.orbit.app.ingestion.dto.EntityProfile;
import com.orbit.app.ingestion.dto.ExternalId;
import com.orbit.app.ingestion.IngestionException;
import com.orbit.app.ingestion.dto.RawContent;
import com.orbit.app.ingestion.SourceAdapter;
import com.orbit.app.ingestion.dto.SourceRef;
import com.orbit.app.ingestion.openalex.dto.OpenAlexAuthor;
import com.orbit.app.ingestion.openalex.dto.OpenAlexWork;

/**
 * OpenAlex adapter — ingests an author's top-cited publications as per-work sources.
 * <p>
 * Flow:
 * <ol>
 *   <li>{@code discoverSources()} — fetch author profile + top-cited works (by citation count).
 *       Returns one SourceRef per work (up to 25). Author profile data is cached
 *       on the first work's context for entity creation.</li>
 *   <li>{@code fetch()} — returns the cached work data (no additional API call).</li>
 *   <li>{@code extractHighSignalCore()} — builds core text from primary_topic + title + abstract.
 *       The primary_topic is prepended as a {@code ##} heading so ChunkingService
 *       sets it as the chunk's {@code section} field.</li>
 * </ol>
 * <p>
 * OpenAlex is free, no API key. A {@code mailto} query parameter is added for the
 * polite pool (faster responses, higher rate limits).
 * <p>
 * Abstracts are stored as inverted indices in OpenAlex — we reconstruct them to
 * plain text before embedding.
 */
@Component
public class OpenAlexAdapter implements SourceAdapter {

    private static final Logger log = LoggerFactory.getLogger(OpenAlexAdapter.class);
    private static final String API_BASE = "https://api.openalex.org";
    private static final int MAX_WORKS = 25;

    private final RestClient restClient;

    @Value("${orbit.openalex.mailto:}")
    private String mailto;

    @Value("${orbit.openalex.api-key:}")
    private String apiKey;

    public OpenAlexAdapter() {
        this.restClient = RestClient.builder()
                .baseUrl(API_BASE)
                .build();
    }

    @Override
    public String sourceType() {
        return "orcid_pub";
    }

    // ─── discoverSources: fetch author + works, return one SourceRef per work ───

    @Override
    public List<SourceRef> discoverSources(SourceRef ref) throws IngestionException {
        String authorId = resolveAuthorId(ref);
        log.info("OpenAlex author: {}", authorId);

        // 1. Fetch author profile
        OpenAlexAuthor author = fetchAuthor(authorId);
        log.info("OpenAlex author {}: {} (ORCID: {}, {} works, affiliation: {})",
                authorId, author.displayName(),
                author.orcid() != null ? author.orcid() : "none",
                author.worksCount(),
                author.affiliation() != null ? author.affiliation() : "none");

        // 2. Fetch top-cited works
        List<OpenAlexWork> works = fetchWorks(author.openalexId());
        log.info("OpenAlex author {}: fetched {} works", authorId, works.size());

        if (works.isEmpty()) {
            log.warn("OpenAlex author {} has no works", authorId);
            return List.of();
        }

        // 3. Build one SourceRef per work
        List<SourceRef> subSources = new ArrayList<>();
        for (int i = 0; i < works.size(); i++) {
            OpenAlexWork work = works.get(i);
            String sourceUrl = "https://openalex.org/" + work.openalexId();

            Map<String, String> context = new HashMap<>();
            context.put("openalex_author_id", author.openalexId());
            context.put("orcid", author.orcid() != null ? author.orcid() : "");
            context.put("openalex_work_id", work.openalexId());
            context.put("work_title", work.title());
            context.put("work_year", String.valueOf(work.publicationYear()));
            context.put("work_doi", work.doi() != null ? work.doi() : "");
            context.put("work_primary_topic", work.primaryTopic());
            context.put("work_abstract", work.reconstructedAbstract());
            context.put("work_cited_by", String.valueOf(work.citedByCount()));
            context.put("work_type", work.type());

            // Cache author profile on first work for entity creation
            if (i == 0) {
                context.put("is_first_work", "true");
                context.put("display_name", author.displayName());
                context.put("affiliation", author.affiliation() != null ? author.affiliation() : "");
                context.put("works_count", String.valueOf(author.worksCount()));
            }

            subSources.add(new SourceRef(
                    sourceUrl,
                    "orcid_pub",
                    "harvested",
                    "public_data",
                    context,
                    ref.ownerId(),
                    ref.onboardingDraft(),
                    ref.profileMode()
            ));
        }

        return subSources;
    }

    // ─── fetch: return cached work data (no API call) ───

    @Override
    public RawContent fetch(SourceRef ref) throws IngestionException {
        Map<String, Object> metadata = new HashMap<>(ref.context());
        String abstractText = ref.context().getOrDefault("work_abstract", "");
        return new RawContent(abstractText, metadata, Instant.now());
    }

    // ─── extractHighSignalCore: primary_topic (as ## heading) + title + abstract ───

    @Override
    public String extractHighSignalCore(RawContent raw) {
        Map<String, Object> meta = raw.metadata();
        String primaryTopic = (String) meta.get("work_primary_topic");
        String title = (String) meta.get("work_title");
        String abstractText = (String) meta.get("work_abstract");
        String year = (String) meta.get("work_year");
        String citedBy = String.valueOf(meta.getOrDefault("work_cited_by", ""));

        StringBuilder sb = new StringBuilder();
        // Prepend primary_topic as ## heading → ChunkingService sets it as chunk section
        if (primaryTopic != null && !primaryTopic.isBlank()) {
            sb.append("## ").append(primaryTopic).append("\n\n");
        }
        if (title != null && !title.isBlank()) {
            sb.append(stripHtml(title)).append("\n\n");
        }
        if (abstractText != null && !abstractText.isBlank()) {
            sb.append(stripHtml(abstractText));
        } else {
            // No abstract — enrich with metadata so the chunk isn't tiny
            // (a bare title + topic heading is only ~15 tokens)
            StringBuilder enrich = new StringBuilder();
            if (year != null && !year.isBlank() && !"null".equals(year)) {
                enrich.append("Published in ").append(year).append(".");
            }
            if (!citedBy.isBlank() && !"null".equals(citedBy) && !"0".equals(citedBy)) {
                enrich.append(" Cited by ").append(citedBy).append(".");
            }
            if (primaryTopic != null && !primaryTopic.isBlank()) {
                enrich.append(" Research area: ").append(primaryTopic).append(".");
            }
            if (!enrich.isEmpty()) {
                sb.append(enrich);
            }
        }
        return sb.toString().trim();
    }

    @Override
    public String buildLabel(RawContent raw) {
        Map<String, Object> meta = raw.metadata();
        String title = (String) meta.get("work_title");
        String year = (String) meta.get("work_year");
        if (title == null || title.isBlank()) title = "Untitled";
        return "«" + title + "» (" + year + ")";
    }

    @Override
    public List<ExternalId> extractExternalIds(RawContent raw) {
        Map<String, Object> meta = raw.metadata();
        List<ExternalId> ids = new ArrayList<>();
        // ORCID — primary identifier for entity resolution (universal, persistent)
        String orcid = (String) meta.get("orcid");
        if (orcid != null && !orcid.isBlank()) {
            ids.add(new ExternalId("orcid", orcid));
        }
        // OpenAlex author ID — secondary, always available (even when ORCID is missing)
        String openalexAuthorId = (String) meta.get("openalex_author_id");
        if (openalexAuthorId != null && !openalexAuthorId.isBlank()) {
            ids.add(new ExternalId("openalex_author_id", openalexAuthorId));
        }
        // DOI — per-work, for citation linking
        String doi = (String) meta.get("work_doi");
        if (doi != null && !doi.isBlank()) {
            ids.add(new ExternalId("doi", doi));
        }
        return ids;
    }

    @Override
    public EntityProfile deriveProfile(RawContent raw) {
        Map<String, Object> meta = raw.metadata();
        String displayName = (String) meta.getOrDefault("display_name", "");
        String affiliation = (String) meta.getOrDefault("affiliation", "");
        String orcid = (String) meta.getOrDefault("orcid", "");

        String name = displayName != null && !displayName.isBlank() ? displayName : "Researcher";
        // Headline is left null on purpose: OpenAlex has no job title, and using the
        // (often stale) institution as the headline just duplicates org_name. The
        // post-ingestion enrichment derives a real role headline from the summary
        // (§2b). org_name keeps the institution as the workplace signal.
        return new EntityProfile(
                "person",
                name,
                null,
                affiliation != null && !affiliation.isBlank() ? affiliation : null,
                null, null,
                Map.of("orcid", orcid != null ? orcid : "")
        );
    }

    // ─── OpenAlex API calls ───

    /**
     * Resolve the author lookup key from the context. ORCID is the primary input.
     * Falls back to OpenAlex author ID if ORCID is not provided.
     * <p>
     * Accepts:
     * - ORCID: "0000-0001-2345-5678" or "https://orcid.org/0000-0001-2345-5678"
     * - OpenAlex ID: "A5086300447" or "https://openalex.org/authors/A5086300447"
     */
    private String resolveAuthorId(SourceRef ref) throws IngestionException {
        // Primary: ORCID (universal, persistent researcher identifier)
        String orcid = ref.context().get("orcid");
        if (orcid != null && !orcid.isBlank()) {
            // Strip URL prefix if present
            if (orcid.startsWith("https://orcid.org/")) {
                orcid = orcid.substring("https://orcid.org/".length());
            }
            return orcid;
        }

        // Fallback: OpenAlex author ID
        String authorId = ref.context().get("openalex_author_id");
        if (authorId == null || authorId.isBlank()) {
            throw new IngestionException("orcid or openalex_author_id is required in context");
        }

        // Strip URL prefix if present
        if (authorId.startsWith("https://openalex.org/")) {
            authorId = authorId.substring("https://openalex.org/".length());
        }
        if (authorId.startsWith("authors/")) {
            authorId = authorId.substring("authors/".length());
        }
        return authorId;
    }

    private OpenAlexAuthor fetchAuthor(String authorId) throws IngestionException {
        String path;
        if (authorId.startsWith("A")) {
            // OpenAlex author ID (e.g. A5086300447)
            path = "/authors/" + authorId;
        } else {
            // Assume ORCID (e.g. 0000-0000-0000-0000)
            path = "/authors/https://orcid.org/" + authorId;
        }

        try {
            JsonNode node = fetchJson(path + "?select=id,display_name,orcid,last_known_institutions,works_count,summary_stats");

            String openalexId = extractShortId(node.path("id").asString(""));
            String displayName = node.path("display_name").asString("");
            String orcidUrl = node.path("orcid").asString("");
            String orcid = orcidUrl;
            if (orcid != null && orcidUrl.startsWith("https://orcid.org/")) {
                orcid = orcidUrl.substring("https://orcid.org/".length());
            }

            // Most recent affiliation
            String affiliation = "";
            JsonNode instNodes = node.path("last_known_institutions");
            if (instNodes.isArray() && !instNodes.isEmpty()) {
                affiliation = instNodes.get(0).path("display_name").asString("");
            }

            int worksCount = node.path("works_count").asInt(0);

            return new OpenAlexAuthor(openalexId, displayName, orcid, affiliation, worksCount);
        } catch (Exception e) {
            throw new IngestionException("Failed to fetch OpenAlex author " + authorId, e);
        }
    }

    private List<OpenAlexWork> fetchWorks(String authorOpenAlexId) throws IngestionException {
        try {
            JsonNode node = fetchJson("/works?filter=author.id:" + authorOpenAlexId
                    + "&sort=cited_by_count:desc&per_page=" + MAX_WORKS
                    + "&select=id,title,publication_year,abstract_inverted_index,primary_topic,cited_by_count,doi,type");

            List<OpenAlexWork> works = new ArrayList<>();
            JsonNode results = node.path("results");
            for (JsonNode workNode : results) {
                String workId = extractShortId(workNode.path("id").asString(""));
                String title = workNode.path("title").asString("");
                int pubYear = workNode.path("publication_year").asInt(0);
                String doi = workNode.path("doi").asString("");
                String type = workNode.path("type").asString("");
                int citedBy = workNode.path("cited_by_count").asInt(0);

                String primaryTopic = workNode.path("primary_topic").path("display_name").asString("");

                String abstractText = reconstructAbstract(workNode.path("abstract_inverted_index"));

                works.add(new OpenAlexWork(workId, title, pubYear, doi, type, citedBy, primaryTopic, abstractText));
            }
            return works;
        } catch (Exception e) {
            throw new IngestionException("Failed to fetch works for author " + authorOpenAlexId, e);
        }
    }

    private JsonNode fetchJson(String path) {
        String fullPath = path;
        // API key gives $1/day budget (10× more than without). Free at
        // openalex.org/settings/api. Without it, only $0.10/day.
        if (apiKey != null && !apiKey.isBlank()) {
            fullPath += (path.contains("?") ? "&" : "?") + "api_key=" + apiKey;
        }
        if (mailto != null && !mailto.isBlank()) {
            fullPath += (path.contains("?") ? "&" : "?") + "mailto=" + mailto;
        }
        // Use URI.create to prevent RestClient from re-encoding the ORCID URL in the path
        // (e.g. /authors/https://orcid.org/... — the :// must stay literal)
        java.net.URI uri = java.net.URI.create(API_BASE + fullPath);
        return restClient.get().uri(uri).retrieve().body(JsonNode.class);
    }

    /**
     * Extract the short ID from a full OpenAlex URL.
     * "https://openalex.org/W2741802027" → "W2741802027"
     */
    private static String extractShortId(String fullId) {
        if (fullId == null || fullId.isBlank()) return "";
        if (fullId.startsWith("https://openalex.org/")) {
            return fullId.substring("https://openalex.org/".length());
        }
        return fullId;
    }

    /**
     * Strip HTML tags from text (e.g. <strong>, <em> from OpenAlex abstracts).
     */
    private static String stripHtml(String text) {
        if (text == null) return "";
        return text.replaceAll("<[^>]+>", "").trim();
    }

    /**
     * Reconstruct plain-text abstract from OpenAlex inverted index.
     * <p>
     * OpenAlex stores abstracts as: {"word": [pos1, pos2, ...], ...}
     * We reverse this into a word array and join with spaces.
     */
    static String reconstructAbstract(JsonNode invertedIndex) {
        if (invertedIndex == null || invertedIndex.isMissingNode() || invertedIndex.isNull()) {
            return "";
        }

        // First pass: find max position
        int maxPos = 0;
        for (var entry : invertedIndex.properties()) {
            for (JsonNode pos : entry.getValue()) {
                int p = pos.asInt();
                if (p > maxPos) maxPos = p;
            }
        }

        // Second pass: place words at their positions
        String[] words = new String[maxPos + 1];
        for (var entry : invertedIndex.properties()) {
            String word = entry.getKey();
            for (JsonNode pos : entry.getValue()) {
                words[pos.asInt()] = word;
            }
        }

        return String.join(" ", words).trim();
    }
}
