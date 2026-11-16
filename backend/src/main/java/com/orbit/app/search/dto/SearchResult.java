package com.orbit.app.search.dto;

import java.util.List;

import com.orbit.app.controllers.dto.EntityDto;

/**
 * A single search result — the full entity read model plus the search-specific
 * signals: the re-ranked {@code score} and the evidence sources that matched.
 * <p>
 * The grounded rationale is not part of the result: the card requests it
 * separately ({@code POST /search/rationale} → {@link SearchRationale}), so the
 * ranking is shown without waiting for the LLM. Until then the card shows the
 * entity summary and these {@code citations}. {@code distance} is a best-effort
 * "X km" label computed at query time when the scope has a geo anchor; it is
 * not stored.
 *
 * @param entity    the full entity read model (identity, availability, sources, …)
 * @param score     final score after re-ranking (§6.3)
 * @param citations the matching evidence sources → the chips
 * @param distance  best-effort distance label, or {@code null}
 */
public record SearchResult(
        EntityDto entity,
        double score,
        List<Citation> citations,
        String distance) {
}
