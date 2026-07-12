package com.orbit.app.search.dto;

import java.util.List;

/**
 * Search response with quality assessment. When {@code sufficient} is false,
 * the results list is empty (all candidates scored below the similarity
 * threshold) and the frontend should prompt the user to create an Anfrage
 * instead of showing weak matches (spec §4: "reicht die Suche nicht").
 *
 * @param query      the original query text (echoed for the frontend prompt)
 * @param sufficient true if at least one result met the quality threshold
 * @param results    ranked results (empty when sufficient = false)
 */
public record SearchResponse(
        String query,
        boolean sufficient,
        List<SearchResult> results
) {}