package com.orbit.app.ingestion.website.dto;

/**
 * An internal link discovered during crawling, with its priority score.
 * Used by {@code WebsiteContentExtractor.discoverLinks()} to rank pages.
 */
public record ScoredLink(String url, int score) {
}