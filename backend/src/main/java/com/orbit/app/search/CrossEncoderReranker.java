package com.orbit.app.search;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.orbit.domain.entity.DiscoveryEntity;

/**
 * Cross-encoder reranker — the deterministic relevance gatekeeper.
 * <p>
 * Calls the configured BAAI/bge-reranker-v2-m3 provider, which scores each
 * (query, document) pair and returns sigmoid-normalised scores in [0, 1].
 * Candidates below {@link #relevanceThreshold} are dropped — this replaces the
 * previous LLM-based relevance filter with a deterministic, fast, industry-standard
 * cross-encoder.
 * <p>
 * Industry-standard 2-stage retrieval:
 * <ol>
 *   <li>Stage 1 — Bi-encoder retrieval (E5 + BM25/FTS + RRF) in {@link SearchService}</li>
 *   <li>Stage 2 — Cross-encoder reranking (this service, ~50 ms for 10 candidates)</li>
 * </ol>
 * The LLM (Gemini) is then used <em>only</em> for generating the German rationale +
 * citations (explanation layer), never for filtering decisions.
 */
@Service
public class CrossEncoderReranker {

    private static final Logger log = LoggerFactory.getLogger(CrossEncoderReranker.class);

    private final CrossEncoderClient rerankerClient;
    private final double relevanceThreshold;

    public CrossEncoderReranker(
            CrossEncoderClient rerankerClient,
            @Value("${orbit.search.reranker-threshold:0.55}") double relevanceThreshold) {
        this.rerankerClient = rerankerClient;
        this.relevanceThreshold = relevanceThreshold;
        log.info("Cross-encoder reranker: provider={}, threshold={}",
                rerankerClient.getClass().getSimpleName(), relevanceThreshold);
    }

    /**
     * Re-score and filter candidates using the cross-encoder. Returns only
     * candidates whose reranker score is ≥ the threshold, sorted by score
     * descending. Provider failures propagate so the API can return 503 rather
     * than display results that did not pass the cross-encoder.
     *
     * @param query      the (translated) English query
     * @param candidates the candidates to rerank (entity + evidence)
     * @return filtered + re-sorted candidates
     */
    public List<RerankResult> rerank(String query, List<RerankCandidate> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }

        // Build document texts from candidate evidence (name + truncated summary)
        List<String> documents = candidates.stream()
                .map(this::buildDocumentText)
                .toList();

        List<Double> scores = rerankerClient.score(query, documents);
        if (scores.size() != candidates.size()) {
            throw new IllegalStateException("Cross-encoder returned the wrong number of scores");
        }

        List<RerankResult> filtered = new ArrayList<>();
        for (int i = 0; i < scores.size(); i++) {
            double score = scores.get(i);
            if (score >= relevanceThreshold) {
                filtered.add(new RerankResult(candidates.get(i), score));
            } else {
                log.info("Reranker dropped \"{}\" (score={} < {})",
                        candidates.get(i).entity().getDisplayName(),
                        String.format("%.4f", score), relevanceThreshold);
            }
        }
        filtered.sort(java.util.Comparator.comparingDouble(RerankResult::rerankerScore).reversed());

        log.info("Reranker: {} candidates → {} passed threshold (scores: {})",
                candidates.size(), filtered.size(),
                scores.stream().map(s -> String.format("%.3f", s)).toList());

        return filtered;
    }

    /** Build the document text the cross-encoder sees for each candidate. */
    private String buildDocumentText(RerankCandidate c) {
        StringBuilder sb = new StringBuilder();
        sb.append(c.entity().getDisplayName());
        if (c.entity().getHeadline() != null && !c.entity().getHeadline().isBlank()) {
            sb.append(": ").append(c.entity().getHeadline());
        }
        if (c.entity().getSummary() != null && !c.entity().getSummary().isBlank()) {
            sb.append(". ").append(truncateSummary(c.entity().getSummary()));
        }
        // Evidence chunks are NOT appended — testing showed they dilute the
        // cross-encoder signal (methodology/acronym noise pushes scores toward
        // neutral). The chunk is still used downstream for grounding/citations.
        return sb.toString();
    }

    /**
     * Truncate the summary to the first 3 sentences (max ~300 chars). The
     * first sentences contain the core expertise statement; later sentences
     * often list specific publications or tangential topics that dilute the
     * cross-encoder signal (testing showed a 0.12 score drop from full vs.
     * truncated summary for multi-topic researchers).
     */
    private String truncateSummary(String summary) {
        // Split on sentence boundaries, keep first 3 sentences.
        String[] sentences = summary.split("(?<=[.!?])\\s+");
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String s : sentences) {
            if (count > 0) sb.append(" ");
            sb.append(s.trim());
            if (++count >= 3) break;
        }
        String result = sb.toString();
        // Hard cap at 300 chars to avoid edge cases with very long sentences.
        return result.length() > 300 ? result.substring(0, 300) : result;
    }

    // ─── Public records ───

    /** Input: a candidate with its entity, base score, and top evidence text. */
    public record RerankCandidate(DiscoveryEntity entity, double baseScore, String topEvidence) {
    }

    /** Output: a candidate with its cross-encoder score. */
    public record RerankResult(RerankCandidate candidate, double rerankerScore) {
    }
}
