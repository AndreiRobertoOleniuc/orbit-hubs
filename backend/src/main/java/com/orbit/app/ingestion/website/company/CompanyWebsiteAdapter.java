package com.orbit.app.ingestion.website.company;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * Company Website adapter — multi-page priority crawl of a company's website.
 * <p>
 * Uses {@link WebsiteContentExtractor} for crawling and boilerplate removal.
 * Crawls up to 10 pages, prioritizing about/team/services/products pages.
 * Extracts CHE UID from imprint page for cross-source entity resolution with Zefix.
 * <p>
 * {@code provenance = "harvested"}, {@code consent_basis = "public_data"}.
 * External IDs: {@code domain} and optionally {@code zefix_uid} (if found in imprint).
 */
@Component
public class CompanyWebsiteAdapter implements SourceAdapter {

    private static final Logger log = LoggerFactory.getLogger(CompanyWebsiteAdapter.class);
    private static final int MAX_PAGES = 10;
    private static final int CONTENT_CAP = 8000;

    /** CHE-NNN.NNN.NNN or CHENNNNNNNNNN */
    private static final Pattern UID_PATTERN =
            Pattern.compile("CHE[-\\s]?(\\d{3})[-\\s.]?(\\d{3})[-\\s.]?(\\d{3})");

    private final WebsiteContentExtractor extractor;
    private final LlmSummaryService llmSummaryService;

    public CompanyWebsiteAdapter(WebsiteContentExtractor extractor, LlmSummaryService llmSummaryService) {
        this.extractor = extractor;
        this.llmSummaryService = llmSummaryService;
    }

    @Override
    public String sourceType() {
        return "company_site";
    }

    // ─── fetch: crawl the company website ───

    @Override
    public RawContent fetch(SourceRef ref) throws IngestionException {
        String url = ref.context().get("url");
        if (url == null || url.isBlank()) {
            throw new IngestionException("url is required in context for company website ingestion");
        }

        String domain = WebsiteContentExtractor.normalizeDomain(url);
        log.info("Company website crawl: {} (domain={})", url, domain);

        List<PageResult> pages = extractor.crawl(url, MAX_PAGES, domain);

        // Collect page texts, ordered by priority score (highest first)
        pages.sort((a, b) -> Integer.compare(b.score(), a.score()));

        StringBuilder allText = new StringBuilder();
        List<String> crawledUrls = new java.util.ArrayList<>();
        String siteName = null;
        String zefixUid = null;

        for (PageResult page : pages) {
            if (!page.ok()) continue;
            crawledUrls.add(page.url());

            if (siteName == null && !page.title().isBlank()) {
                siteName = page.title();
            }

            // Try to extract CHE UID from imprint/legal pages
            if (zefixUid == null && page.text() != null) {
                zefixUid = extractUid(page.text());
            }

            allText.append("--- ").append(page.title()).append(" ---\n\n");
            allText.append(page.text()).append("\n\n");

            // Cap total content
            if (allText.length() >= CONTENT_CAP) {
                log.info("Content cap reached at {} chars, truncating", allText.length());
                allText.setLength(CONTENT_CAP);
                break;
            }
        }

        if (allText.isEmpty()) {
            throw new IngestionException(
                    "No content extracted from " + url
                    + " — site may be JS-rendered or returned empty content");
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("domain", domain);
        metadata.put("url", url);
        metadata.put("page_count", crawledUrls.size());
        metadata.put("crawled_urls", crawledUrls);
        metadata.put("site_name", siteName);
        metadata.put("zefix_uid", zefixUid); // may be null
        metadata.put("raw_text", allText.toString());

        if (zefixUid != null) {
            log.info("Found CHE UID in website content: {}", zefixUid);
        }

        return new RawContent(allText.toString(), metadata, Instant.now());
    }

    // ─── extractHighSignalCore ───

    @Override
    public String extractHighSignalCore(RawContent raw) {
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = raw.metadata();
        String domain = (String) meta.getOrDefault("domain", "");
        String siteName = (String) meta.getOrDefault("site_name", "");
        String rawText = (String) meta.getOrDefault("raw_text", "");

        // Try Gemini summarization first — produces clean, focused, searchable text
        String summary = llmSummaryService.summarizeWebsiteContent(domain, siteName, "organization", rawText);
        if (summary != null && !summary.isBlank()) {
            log.debug("LLM website summary for {}: {} chars", domain, summary.length());
            return summary;
        }

        // LLM failed — fall back to raw text (already cleaned by WebsiteContentExtractor)
        log.warn("LLM summarization failed for {} — using raw extracted text", domain);
        StringBuilder sb = new StringBuilder();
        sb.append("=== COMPANY WEBSITE: ").append(domain).append(" ===\n\n");
        sb.append(rawText);
        return sb.toString().trim();
    }

    @Override
    public String buildLabel(RawContent raw) {
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = raw.metadata();
        String siteName = (String) meta.getOrDefault("site_name", "");
        String domain = (String) meta.getOrDefault("domain", "");
        return !siteName.isBlank() ? siteName : domain;
    }

    @Override
    public List<ExternalId> extractExternalIds(RawContent raw) {
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = raw.metadata();
        java.util.List<ExternalId> ids = new java.util.ArrayList<>();

        String domain = (String) meta.get("domain");
        if (domain != null && !domain.isBlank()) {
            ids.add(new ExternalId("domain", domain));
        }

        // If we found a CHE UID in the imprint, add it for Zefix cross-matching
        String zefixUid = (String) meta.get("zefix_uid");
        if (zefixUid != null && !zefixUid.isBlank()) {
            ids.add(new ExternalId("zefix_uid", zefixUid));
        }

        return ids;
    }

    @Override
    public EntityProfile deriveProfile(RawContent raw) {
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = raw.metadata();
        String domain = (String) meta.getOrDefault("domain", "");
        String siteName = (String) meta.getOrDefault("site_name", "");

        String displayName = (siteName != null && !siteName.isBlank()) ? siteName : domain;
        String headline = "Company website: " + domain;

        Map<String, Object> profileMeta = new HashMap<>();
        profileMeta.put("domain", domain);
        profileMeta.put("crawl_page_count", meta.get("page_count"));
        if (meta.get("zefix_uid") != null) {
            profileMeta.put("zefix_uid", meta.get("zefix_uid"));
        }

        return new EntityProfile(
                "organization",
                displayName,
                headline,
                displayName,   // orgName = site name
                null,          // latitude
                null,          // longitude
                profileMeta
        );
    }

    // ─── CHE UID extraction from imprint text ───

    /**
     * Extract a CHE-NNN.NNN.NNN UID from page text (typically imprint/Impressum).
     * @return normalized UID (CHE-NNN.NNN.NNN) or null if not found
     */
    private static String extractUid(String text) {
        Matcher m = UID_PATTERN.matcher(text);
        if (m.find()) {
            // Normalize to CHE-NNN.NNN.NNN
            return "CHE-" + m.group(1) + "." + m.group(2) + "." + m.group(3);
        }
        return null;
    }
}