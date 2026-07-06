package com.orbit.app.ingestion.website;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.orbit.app.ingestion.website.dto.PageResult;
import com.orbit.app.ingestion.website.dto.ScoredLink;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Shared utility for fetching and extracting content from websites.
 * <p>
 * Used by both {@code PersonalWebsiteAdapter} and {@code CompanyWebsiteAdapter}.
 * Implements a 3-layer boilerplate removal strategy:
 * <ol>
 *   <li>Remove known noise containers (nav, footer, cookie banners, etc.)</li>
 *   <li>Find the main content container (semantic HTML → class patterns → body)</li>
 *   <li>Extract structured text preserving ## headings for ChunkingService</li>
 * </ol>
 */
@Component
public class WebsiteContentExtractor {

    private static final Logger log = LoggerFactory.getLogger(WebsiteContentExtractor.class);

    private static final String USER_AGENT =
            "Orbit-Discovery/1.0 (mailto:contact@orbit.ch)";
    private static final Duration POLITENESS_DELAY = Duration.ofMillis(1000);
    private static final int FETCH_TIMEOUT_MS = 10_000;

    // ─── Public API ───

    /**
     * Crawl a website: fetch the homepage, discover internal links, score them,
     * and fetch the top-N highest-value pages. Returns all extracted page texts.
     *
     * @param startUrl root URL to start crawling from
     * @param maxPages maximum number of pages to fetch (including homepage)
     * @param domain the domain to restrict crawling to (links outside are skipped)
     * @return list of PageResult for each fetched page (sorted by score, homepage first)
     */
    public List<PageResult> crawl(String startUrl, int maxPages, String domain) {
        List<PageResult> results = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        List<String> queue = new ArrayList<>(); // URL → score via discoveredLinks

        // 1. Fetch homepage
        log.info("Crawling {} (max {} pages, domain={})", startUrl, maxPages, domain);
        PageResult home = fetchAndExtract(startUrl, 50); // homepage score = 50
        if (home.ok()) {
            results.add(home);
        } else {
            log.warn("Homepage fetch failed for {} — aborting crawl", startUrl);
            return results;
        }
        visited.add(normalizeUrl(startUrl));

        // 2. Discover internal links from the homepage
        Document doc = home.ok() ? parseDoc(home.text()) : null;
        // Re-fetch homepage as Document for link extraction
        try {
            Document homeDoc = Jsoup.connect(startUrl)
                    .userAgent(USER_AGENT)
                    .timeout(FETCH_TIMEOUT_MS)
                    .get();
            List<ScoredLink> links = discoverLinks(homeDoc, domain, visited);
            links.sort((a, b) -> Integer.compare(b.score(), a.score()));

            log.info("Discovered {} internal links from homepage (top: {})",
                    links.size(),
                    links.stream().limit(5).map(l -> l.score() + ":" + l.url()).toList());

            // 3. Fetch top-N pages
            for (ScoredLink link : links) {
                if (results.size() >= maxPages) break;
                if (visited.contains(normalizeUrl(link.url()))) continue;

                sleepPoliteness();
                PageResult page = fetchAndExtract(link.url(), link.score());
                if (page.ok()) {
                    results.add(page);
                    visited.add(normalizeUrl(link.url()));
                }
            }
        } catch (Exception e) {
            log.warn("Link discovery failed for {}: {}", startUrl, e.getMessage());
        }

        log.info("Crawl complete: {} pages fetched from {}", results.size(), domain);
        return results;
    }

    /**
     * Normalize a domain from a URL (strip www., protocol, path).
     * e.g. "https://www.acme.ch/about" → "acme.ch"
     */
    public static String normalizeDomain(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null) return url;
            if (host.startsWith("www.")) host = host.substring(4);
            return host;
        } catch (Exception e) {
            return url;
        }
    }

    // ─── Internal: fetch + extract ───

    private PageResult fetchAndExtract(String url, int score) {
        try {
            Connection conn = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(FETCH_TIMEOUT_MS)
                    .followRedirects(true)
                    .ignoreHttpErrors(true)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9,de;q=0.8,fr;q=0.7");

            Connection.Response resp = conn.execute();
            int status = resp.statusCode();
            if (status != 200 && status != 510) {
                log.warn("HTTP {} for {}", status, url);
                return new PageResult(url, "", "", score, false);
            }
            if (status == 510) {
                log.info("HTTP 510 for {} — attempting to parse body anyway", url);
            }

            // Skip non-HTML
            String contentType = resp.contentType();
            if (contentType != null && !contentType.contains("text/html")) {
                log.debug("Skipping non-HTML page: {} ({})", url, contentType);
                return new PageResult(url, "", "", score, false);
            }

            Document doc = conn.get();
            String title = doc.title() != null ? doc.title().trim() : "";

            // Capture body text BEFORE noise removal as last-resort fallback
            String rawBodyText = doc.body() != null ? doc.body().text() : "";

            // 3-layer boilerplate removal
            removeNoiseContainers(doc);
            Element main = findMainContent(doc);
            String text = extractStructuredText(main, doc);

            // Fallback 1: if structured extraction produces too little, use body.text() (post-removal)
            if ((text == null || text.length() < 50) && doc.body() != null) {
                String fallbackText = doc.body().text();
                if (fallbackText != null && fallbackText.length() > 50) {
                    log.info("Structured extraction yielded {} chars for {}, falling back to body.text() ({} chars)",
                            text != null ? text.length() : 0, url, fallbackText.length());
                    text = fallbackText;
                }
            }

            // Fallback 2: if still too little, use the raw body text (pre-noise-removal)
            if ((text == null || text.length() < 50) && rawBodyText.length() > 50) {
                log.info("Falling back to raw body text (pre-noise-removal) for {}: {} chars", url, rawBodyText.length());
                text = rawBodyText;
            }

            if (text == null || text.isBlank() || text.length() < 50) {
                log.warn("Extracted text too short ({} chars) for {} — might be JS-rendered", text.length(), url);
                return new PageResult(url, title, text, score, false);
            }

            log.debug("Extracted {} chars from {} (title: '{}')", text.length(), url, title);
            return new PageResult(url, title, text, score, true);

        } catch (Exception e) {
            log.warn("Fetch failed for {}: {} ({})", url, e.getMessage(), e.getClass().getSimpleName());
            return new PageResult(url, "", "", score, false);
        }
    }

    // ─── Layer 1: Remove noise containers ───

    private void removeNoiseContainers(Document doc) {
        // Scripts, styles, embedded media
        doc.select("script, style, noscript, iframe, svg, canvas, template").remove();

        // Semantic boilerplate elements
        doc.select("nav, header, footer, aside").remove();
        doc.select("[role='navigation'], [role='banner'], [role='contentinfo']").remove();

        // Common boilerplate class/ID patterns
        doc.select(
                "[class*='navbar'], [class*='nav-menu'], [class*='nav-bar'], [class*='navigation'], "
                + "[class*='main-menu'], [class*='side-menu'], [class*='sidebar'], "
                + "[class*='footer'], [class*='cookie'], [class*='banner'], [class*='popup'], "
                + "[class*='modal'], [class*='overlay'], [class*='ad-'], [class*='adsense'], "
                + "[class*='sponsor'], [class*='newsletter'], [class*='signup'], [class*='subscribe'], "
                + "[class*='social-share'], [class*='breadcrumb'], "
                + "[class*='testimonial'], [class*='carousel'], [class*='slider'], "
                + "[class*='mobile-menu'], [class*='mega-menu'], [class*='megaMenu'], "
                + "[id*='cookie'], [id*='banner'], [id*='popup']"
        ).remove();

        // Form elements (contact forms, search bars)
        doc.select("form").remove();
    }

    // ─── Layer 2: Find main content container ───

    private Element findMainContent(Document doc) {
        // Priority 1: semantic HTML
        Element main = doc.selectFirst("main, article, [role='main']");
        if (main != null && main.text().length() > 50) return main;

        // Priority 2: common class/ID patterns
        main = doc.selectFirst(
                "[class*='content'], [class*='main-content'], [class*='page-content'],"
                + "[class*='entry-content'], [class*='post-content'], [class*='main__'],"
                + "#content, #main, #primary"
        );
        if (main != null && main.text().length() > 50) return main;

        // Priority 3: largest div by text length
        Element largest = null;
        int largestLen = 0;
        for (Element div : doc.select("div, section")) {
            int len = div.ownText().length() + div.children().stream()
                    .mapToInt(e -> e.text().length()).sum();
            if (len > largestLen) {
                largestLen = len;
                largest = div;
            }
        }
        if (largest != null && largestLen > 100) return largest;

        // Fallback: body
        return doc.body();
    }

    // ─── Layer 3: Extract structured text with ## headings ───

    private String extractStructuredText(Element container, Document doc) {
        if (container == null) return "";
        StringBuilder text = new StringBuilder();
        extractRecursive(container, text);
        return cleanWhitespace(text.toString());
    }

    private void extractRecursive(Element parent, StringBuilder text) {
        for (Element el : parent.children()) {
            String tag = el.tagName();
            switch (tag) {
                case "h1", "h2", "h3", "h4", "h5", "h6" -> {
                    String heading = el.text().trim();
                    if (!heading.isEmpty()) {
                        text.append("## ").append(heading).append("\n\n");
                    }
                }
                case "p" -> {
                    String t = el.text().trim();
                    if (!t.isEmpty()) text.append(t).append("\n\n");
                }
                case "li" -> {
                    // Handled by ul/ol below — skip direct li to avoid duplication
                }
                case "ul", "ol" -> {
                    for (Element li : el.select("> li")) {
                        String t = li.text().trim();
                        if (!t.isEmpty()) text.append("- ").append(t).append("\n");
                    }
                    text.append("\n");
                }
                case "dl" -> {
                    for (Element dt : el.select("> dt")) {
                        String t = dt.text().trim();
                        if (!t.isEmpty()) text.append("**").append(t).append("** ");
                    }
                    for (Element dd : el.select("> dd")) {
                        String t = dd.text().trim();
                        if (!t.isEmpty()) text.append(t).append("\n\n");
                    }
                }
                case "blockquote" -> {
                    String t = el.text().trim();
                    if (!t.isEmpty()) text.append("> ").append(t).append("\n\n");
                }
                case "table" -> {
                    // Extract table as simple key-value
                    for (Element row : el.select("tr")) {
                        String rowText = row.select("td, th").stream()
                                .map(Element::text)
                                .reduce((a, b) -> a + " | " + b)
                                .orElse("");
                        if (!rowText.isEmpty()) text.append(rowText).append("\n");
                    }
                    text.append("\n");
                }
                case "div", "section", "main", "article" -> {
                    // Recurse into containers
                    extractRecursive(el, text);
                }
                case "br" -> text.append("\n");
                case "hr" -> text.append("\n\n");
                default -> {
                    // For other inline elements, grab text directly
                    if (el.textNodes().size() > 0) {
                        String t = el.text().trim();
                        if (!t.isEmpty() && t.length() > 20) {
                            text.append(t).append("\n\n");
                        }
                    }
                }
            }
        }
    }

    // ─── Link discovery + scoring ───

    private List<ScoredLink> discoverLinks(Document doc, String domain, Set<String> visited) {
        List<ScoredLink> links = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        Elements anchors = doc.select("a[href]");
        for (Element a : anchors) {
            String href = a.absUrl("href");
            if (href == null || href.isBlank()) continue;

            // Normalize: strip fragment, trailing slash
            href = normalizeUrl(href);

            // Only same-domain links
            if (!isSameDomain(href, domain)) continue;
            if (visited.contains(href) || seen.contains(href)) continue;

            // Skip non-HTML resources
            if (isBinaryUrl(href)) continue;

            int score = scoreUrl(href);
            links.add(new ScoredLink(href, score));
            seen.add(href);
        }

        return links;
    }

    /**
     * Score a URL by its path patterns (higher = more valuable for discovery).
     */
    private int scoreUrl(String url) {
        String path;
        try {
            path = URI.create(url).getPath().toLowerCase();
        } catch (Exception e) {
            return 15; // default
        }

        // High-value pages
        if (path.matches(".*/(about|ueber-uns|a-propos|uber-uns)(/.*)?$")) return 100;
        if (path.matches(".*/(team|mitarbeiter|equipe)(/.*)?$")) return 95;
        if (path.matches(".*/(services|leistungen|prestations|offres)(/.*)?$")) return 90;
        if (path.matches(".*/(products|produkte|produits)(/.*)?$")) return 85;
        if (path.matches(".*/(portfolio|projekte|projects|projets|references|referenzen)(/.*)?$")) return 80;
        if (path.matches(".*/(expertise|kompetenzen|competences)(/.*)?$")) return 80;
        if (path.matches(".*/(solutions|loesungen|solutions)(/.*)?$")) return 75;
        if (path.matches(".*/(case-studies|case_study|references|referenzen)(/.*)?$")) return 70;
        if (path.matches(".*/(technology|technologie|tech-stack)(/.*)?$")) return 65;
        if (path.matches(".*/(blog|news|aktuelles)(/.*)?$")) return 20;
        if (path.matches(".*/(impressum|imprint|legal|privacy|datenschutz)(/.*)?$")) return 10;
        if (path.matches(".*/(contact|kontakt)(/.*)?$")) return 30;
        if (path.matches(".*/(careers|jobs|stellen)(/.*)?$")) return 25;
        if (path.equals("/") || path.isEmpty()) return 50;

        return 15; // unknown
    }

    private boolean isSameDomain(String url, String domain) {
        String urlDomain = normalizeDomain(url);
        return urlDomain.equalsIgnoreCase(domain) || urlDomain.endsWith("." + domain);
    }

    private boolean isBinaryUrl(String url) {
        String lower = url.toLowerCase();
        return lower.matches(".*\\.(pdf|jpg|jpeg|png|gif|webp|svg|zip|docx?|xlsx?|pptx?|mp4|mp3|wav|css|js|ico|woff2?)$");
    }

    private String normalizeUrl(String url) {
        // Strip fragment
        int hash = url.indexOf('#');
        if (hash >= 0) url = url.substring(0, hash);
        // Strip trailing slash for consistency
        if (url.endsWith("/") && url.length() > 1) url = url.substring(0, url.length() - 1);
        return url;
    }

    // ─── Utilities ───

    private String cleanWhitespace(String text) {
        // Collapse multiple blank lines
        text = text.replaceAll("\\n{3,}", "\n\n");
        // Trim trailing spaces on each line
        String[] lines = text.split("\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append(line.stripTrailing()).append("\n");
        }
        return sb.toString().strip();
    }

    private void sleepPoliteness() {
        try {
            Thread.sleep(POLITENESS_DELAY.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Document parseDoc(String html) {
        return Jsoup.parse(html);
    }
}