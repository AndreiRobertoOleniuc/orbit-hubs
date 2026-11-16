package com.orbit.app.search.dto;

import java.util.List;
import java.util.Map;

/**
 * Per-stage diagnostics for one search — what the evaluation harness reads to
 * attribute a missing profile to the stage that lost it (not retrieved, below
 * the rerank depth, reranker) and to sweep the threshold and depth offline.
 * <p>
 * In a traced search the cross-encoder scores <em>every</em> retrieved
 * candidate, including those below the rerank depth, so the depth can be
 * re-evaluated from the recorded scores. The returned results are unaffected.
 *
 * @param retrievalQuery        the English query text embedded for the semantic leg
 * @param lexicalQuery          the keywords sent to the lexical leg
 * @param originalLanguageLeg   whether the original-language query was embedded as a
 *                              second semantic leg (false when it equals the English text)
 * @param rerankQuery           the condensed query the cross-encoder scored against
 * @param matchCount            how many results the caller asked for
 * @param candidateDepth        how many entities stage 1 retrieved
 * @param rerankDepth           how many of the best-retrieved candidates the cross-encoder scores
 * @param rerankPassages        evidence chunks per candidate it scores, besides the profile document
 * @param rerankerThreshold     the cross-encoder gate in force
 * @param timingsMs             wall time per stage, in pipeline order, plus {@code total}
 * @param candidates            every stage-1 candidate, in retrieval order
 * @param semanticEntityRanking entities ranked by their single best chunk across the
 *                              whole scoped corpus, for comparison with {@code candidates}
 */
public record SearchTrace(
        String retrievalQuery,
        String lexicalQuery,
        boolean originalLanguageLeg,
        String rerankQuery,
        int matchCount,
        int candidateDepth,
        int rerankDepth,
        int rerankPassages,
        double rerankerThreshold,
        Map<String, Long> timingsMs,
        List<CandidateTrace> candidates,
        List<SemanticEntityRank> semanticEntityRanking) {
}
