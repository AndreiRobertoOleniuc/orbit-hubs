package com.orbit.app.search.dto;

import java.util.List;

import com.orbit.app.controllers.dto.EntityDto;

/**
 * A single search result — the full entity read model plus the search-specific
 * signals: the re-ranked {@code score}, a grounded German {@code rationale}
 * (reference §6.4), and the validated {@code citations} that back it (§6.5).
 * <p>
 * {@code rationale} is {@code null} when the LLM is unavailable — the card then
 * degrades to an evidence-only view using {@code citations} + the entity summary.
 * {@code distance} is a best-effort "X km" label computed at query time when the
 * scope has a geo anchor; it is not stored.
 *
 * @param entity    the full entity read model (identity, availability, sources, …)
 * @param score     final score after re-ranking (§6.3)
 * @param rationale grounded German rationale, or {@code null} on fallback
 * @param citations validated cited sources → the chips
 * @param distance  best-effort distance label, or {@code null}
 */
public record SearchResult(
        EntityDto entity,
        double score,
        String rationale,
        List<Citation> citations,
        String distance) {
}
