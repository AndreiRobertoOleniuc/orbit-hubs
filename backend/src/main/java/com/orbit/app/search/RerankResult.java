package com.orbit.app.search;

/** A retrieval candidate paired with its cross-encoder relevance score. */
public record RerankResult(
        RerankCandidate candidate,
        double rerankerScore
) {
}
