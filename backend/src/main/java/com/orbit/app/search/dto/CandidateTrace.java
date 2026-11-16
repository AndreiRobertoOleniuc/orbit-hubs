package com.orbit.app.search.dto;

/**
 * One stage-1 candidate's path through the pipeline.
 *
 * @param entityId        the candidate entity
 * @param displayName     its display name (for readable evaluation output)
 * @param retrievalRank   1-based rank in the fused hybrid retrieval
 * @param rrfScore        fused RRF score from {@code hybrid_search}
 * @param lexicalRank     entity rank in the keyword leg, {@code null} if not in its top candidates
 * @param semanticRank    entity rank in the English-query semantic leg, {@code null} if not in it
 * @param altSemanticRank entity rank in the original-language semantic leg, {@code null} if not in
 *                        it or the leg was off (query already English)
 * @param bestSimilarity  best evidence-chunk similarity to the English query (diagnostic only)
 * @param rerankerScore   cross-encoder score of the best passage, {@code null} if never scored
 * @param rerankedOn      the passage that score came from: a source label or
 *                        {@code "profile document"}; {@code null} if never scored
 * @param boostFactor     soft re-rank multiplier, {@code null} if dropped before it
 * @param finalScore      {@code rerankerScore × boostFactor}, {@code null} if dropped
 * @param finalRank       1-based rank in the returned results, {@code null} if dropped
 * @param droppedAt       {@code "rerank-depth"} (retrieved below the depth the cross-encoder
 *                        scores), {@code "reranker"}, {@code "result-limit"} (relevant but
 *                        ranked below the requested result count), or {@code null} when
 *                        returned
 */
public record CandidateTrace(
        Long entityId,
        String displayName,
        int retrievalRank,
        double rrfScore,
        Integer lexicalRank,
        Integer semanticRank,
        Integer altSemanticRank,
        double bestSimilarity,
        Double rerankerScore,
        String rerankedOn,
        Double boostFactor,
        Double finalScore,
        Integer finalRank,
        String droppedAt) {
}
