package com.orbit.app.search.dto;

import java.util.List;

/**
 * The grounded German rationale of one search result (reference §6.4) and the
 * validated citations that back it (§6.5). {@code rationale} is {@code null}
 * when the LLM is unavailable or none of its claims cited the evidence; the
 * citations are then every evidence source, as on the result card.
 *
 * @param entityId  the explained result
 * @param rationale 1–3 German sentences, or {@code null}
 * @param citations the cited sources → the chips
 */
public record SearchRationale(Long entityId, String rationale, List<Citation> citations) {
}
