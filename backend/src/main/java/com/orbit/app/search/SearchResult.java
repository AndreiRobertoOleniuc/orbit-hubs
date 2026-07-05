package com.orbit.app.search;

import java.util.List;

/**
 * A single search result — one entity with its RRF score and evidence chunks.
 * Evidence chunks are the best-matching chunks for this query, deduped by source
 * (one chunk per source = one citation chip).
 */
public record SearchResult(
        Long entityId,
        String displayName,
        String headline,
        String orgName,
        String entityType,
        double score,
        List<EvidenceChunk> evidence
) {
    /**
     * A chunk that matched the query, with its source label (citation chip text)
     * and similarity score.
     */
    public record EvidenceChunk(
            String content,
            String section,
            String sourceLabel,
            String sourceUrl,
            int tokenCount,
            double similarity
    ) {}
}