package com.orbit.app.search;

/**
 * A retrieval candidate paired with its cross-encoder relevance score: the
 * score of its best passage.
 *
 * @param bestPassage the passage that scored highest, {@code null} when the
 *                    candidate had none (score 0)
 */
public record RerankResult(
        RerankCandidate candidate,
        double rerankerScore,
        RerankPassage bestPassage
) {
}
