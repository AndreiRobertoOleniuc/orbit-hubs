package com.orbit.app.llm.dto;

import java.util.List;

/**
 * The grounded generator's output (reference §6.4): a plain-language German
 * rationale plus the per-claim citations. Consumed by the search pipeline, which
 * validates the citations (§6.5) before anything reaches a card.
 * <p>
 * The LLM only <em>explains</em> the match — it never decides whether a result
 * is relevant. That decision is made by the cross-encoder reranker
 * ({@link com.orbit.app.search.CrossEncoderReranker}) before the LLM is called.
 */
public record MatchRationale(
        String rationale,
        List<RationaleClaim> claims) {
}