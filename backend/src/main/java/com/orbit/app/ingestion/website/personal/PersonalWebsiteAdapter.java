package com.orbit.app.ingestion.website.personal;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.orbit.app.ingestion.IngestionException;
import com.orbit.app.ingestion.SourceAdapter;
import com.orbit.app.ingestion.dto.EntityProfile;
import com.orbit.app.ingestion.dto.ExternalId;
import com.orbit.app.ingestion.dto.RawContent;
import com.orbit.app.ingestion.dto.SourceRef;
import com.orbit.app.ingestion.website.WebsiteContentExtractor;
import com.orbit.app.ingestion.website.WebsiteContentExtractor.PageResult;
import com.orbit.app.llm.LlmSummaryService;

/**
 * Personal Website adapter — crawls a person's website and extracts content
 * for semantic search.
 * <p>
 * Uses {@link WebsiteContentExtractor} for the actual crawling and boilerplate
 * removal. This is a single-source adapter (one URL = one source).
 * <p>
 * {@code provenance = "harvested"}, {@code consent_basis = "public_data"}.
 * External ID: {@code domain} (normalized from the URL).
 */
@Component
public class PersonalWebsiteAdapter implements SourceAdapter {

    private static final Logger log = LoggerFactory.getLogger(PersonalWebsiteAdapter.class);
    private static final int MAX_PAGES = 5;

    private final WebsiteContentExtractor extractor;
    private final LlmSummaryService llmSummaryService;

    public PersonalWebsiteAdapter(WebsiteContentExtractor extractor, LlmSummaryService llmSummaryService) {
        this.extractor = extractor;
        this.llmSummaryService = llmSummaryService;
    }

    @Override
    public String sourceType() {
        return "personal_site";
    }

    // ─── fetch: crawl the website ───

    @Override
    public RawContent fetch(SourceRef ref) throws IngestionException {
        String url = ref.context().get("url");
        if (url == null || url.isBlank()) {
            throw new IngestionException("url is required in context for personal website ingestion");
        }

        String domain = WebsiteContentExtractor.normalizeDomain(url);
        log.info("Personal website crawl: {} (domain={})", url, domain);

        List<PageResult> pages = extractor.crawl(url, MAX_PAGES, domain);

        // Collect page texts
        StringBuilder allText = new StringBuilder();
        List<String> crawledUrls = new java.util.ArrayList<>();
        String siteName = null;
        String metaDescription = null;
        String ogTitle = null;

        for (PageResult page : pages) {
            if (!page.ok()) continue;
            crawledUrls.add(page.url());
            // Use first page's title as site name
            if (siteName == null && !page.title().isBlank()) {
                siteName = page.title();
            }
            allText.append("--- ").append(page.title()).append(" ---\n\n");
            allText.append(page.text()).append("\n\n");
        }

        if (allText.isEmpty()) {
            throw new IngestionException(
                    "No content extracted from " + url
                    + " — site may be JS-rendered or returned empty content");
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("domain", domain);
        metadata.put("url", url);
        metadata.put("page_count", pages.size());
        metadata.put("crawled_urls", crawledUrls);
        metadata.put("site_name", siteName);
        metadata.put("meta_description", metaDescription);
        metadata.put("raw_text", allText.toString());

        return new RawContent(allText.toString(), metadata, Instant.now());
    }

    // ─── extractHighSignalCore: the crawled text is already cleaned ───

    @Override
    public String extractHighSignalCore(RawContent raw) {
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = raw.metadata();
        String domain = (String) meta.getOrDefault("domain", "");
        String siteName = (String) meta.getOrDefault("site_name", "");
        String rawText = (String) meta.getOrDefault("raw_text", "");

        // Try Gemini summarization first — produces clean, focused, searchable text
        String summary = llmSummaryService.summarizeWebsiteContent(domain, siteName, "person", rawText);
        if (summary != null && !summary.isBlank()) {
            log.debug("LLM website summary for {}: {} chars", domain, summary.length());
            return summary;
        }

        // LLM failed — fall back to raw text (already cleaned by WebsiteContentExtractor)
        log.warn("LLM summarization failed for {} — using raw extracted text", domain);
        StringBuilder sb = new StringBuilder();
        sb.append("=== PERSONAL WEBSITE: ").append(domain).append(" ===\n\n");
        sb.append(rawText);
        return sb.toString().trim();
    }

    @Override
    public String buildLabel(RawContent raw) {
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = raw.metadata();
        return (String) meta.getOrDefault("domain", "personal website");
    }

    @Override
    public List<ExternalId> extractExternalIds(RawContent raw) {
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = raw.metadata();
        String domain = (String) meta.get("domain");
        if (domain == null || domain.isBlank()) {
            return List.of();
        }
        return List.of(new ExternalId("domain", domain));
    }

    @Override
    public EntityProfile deriveProfile(RawContent raw) {
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = raw.metadata();
        String domain = (String) meta.getOrDefault("domain", "");
        String siteName = (String) meta.getOrDefault("site_name", "");

        // Use site name as display name, fallback to domain
        String displayName = (siteName != null && !siteName.isBlank()) ? siteName : domain;

        // Headline: domain or first heading
        String headline = "Personal website: " + domain;

        Map<String, Object> profileMeta = new HashMap<>();
        profileMeta.put("domain", domain);
        profileMeta.put("crawl_page_count", meta.get("page_count"));

        return new EntityProfile(
                "person",
                displayName,
                headline,
                null,   // orgName
                null,   // latitude
                null,   // longitude
                profileMeta
        );
    }
}