package com.orbit.app.ingestion.website.dto;

/**
 * Result of fetching a single page during a website crawl.
 */
public record PageResult(
        String url,
        String title,
        String text,           // extracted structured text (with ## headings)
        int score,             // URL priority score
        boolean ok             // false = fetch failed or empty content
) {
}