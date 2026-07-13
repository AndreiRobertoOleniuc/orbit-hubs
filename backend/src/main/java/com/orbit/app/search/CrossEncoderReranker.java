package com.orbit.app.search;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.orbit.domain.entity.DiscoveryEntity;

/**
 * Cross-encoder reranker — the deterministic relevance gatekeeper.
 * <p>
 * Calls a Python microservice (BAAI/bge-reranker-v2-m3) that scores each
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

    private final RestClient rerankerClient;
    private final double relevanceThreshold;

    public CrossEncoderReranker(
            @Value("${orbit.search.reranker-url:http://localhost:8001}") String rerankerUrl,
            @Value("${orbit.search.reranker-threshold:0.55}") double relevanceThreshold) {
        this.rerankerClient = RestClient.builder().baseUrl(rerankerUrl).build();
        this.relevanceThreshold = relevanceThreshold;
        log.info("Cross-encoder reranker: url={}, threshold={}", rerankerUrl, relevanceThreshold);
    }

    /**
     * Re-score and filter candidates using the cross-encoder. Returns only
     * candidates whose reranker score is ≥ the threshold, sorted by score
     * descending. Falls back to returning all candidates (unchanged order) if
     * the reranker service is unavailable — search must never fail because the
     * reranker is down.
     *
     * @param query      the (translated) English query
     * @param candidates the candidates to rerank (entity + evidence)
     * @return filtered + re-sorted candidates
     */
    public List<RerankResult> rerank(String query, List<RerankCandidate> candidates) {
        if (candidates.size() <= 1) {
            return candidates.stream()
                    .map(c -> new RerankResult(c, 1.0))
                    .toList();
        }

        // Build document texts from candidate evidence (name + summary + top chunk)
        List<String> documents = candidates.stream()
                .map(this::buildDocumentText)
                .toList();

        RerankResponse resp;
        try {
            resp = rerankerClient.post()
                    .uri("/rerank")
                    .body(new RerankRequest(query, documents))
                    .retrieve()
                    .body(RerankResponse.class);
        } catch (Exception e) {
            log.warn("Reranker service unavailable ({}), falling back to unfiltered results", e.getMessage());
            return candidates.stream()
                    .map(c -> new RerankResult(c, c.baseScore))
                    .toList();
        }

        if (resp == null || resp.results() == null || resp.results().isEmpty()) {
            log.warn("Reranker returned empty results, falling back");
            return candidates.stream()
                    .map(c -> new RerankResult(c, c.baseScore))
                    .toList();
        }

        List<RerankResult> filtered = new ArrayList<>();
        for (RerankScore rs : resp.results()) {
            if (rs.score() >= relevanceThreshold) {
                filtered.add(new RerankResult(candidates.get(rs.index()), rs.score()));
            } else {
                log.info("Reranker dropped \"{}\" (score={} < {})",
                        candidates.get(rs.index()).entity().getDisplayName(),
                        String.format("%.4f", rs.score()), relevanceThreshold);
            }
        }

        log.info("Reranker: {} candidates → {} passed threshold (scores: {})",
                candidates.size(), filtered.size(),
                resp.results().stream().map(s -> String.format("%.3f", s.score())).toList());

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
            sb.append(". ").append(c.entity().getSummary());
        }
        // Append the top evidence chunk for more context
        if (c.topEvidence() != null && !c.topEvidence().isBlank()) {
            sb.append(" ").append(c.topEvidence());
        }
        return sb.toString();
    }

    // ─── DTOs for the Python service ───

    record RerankRequest(String query, List<String> documents) {
    }

    record RerankScore(int index, double score) {
    }

    record RerankResponse(List<RerankScore> results) {
    }

    // ─── Public records ───

    /** Input: a candidate with its entity, base score, and top evidence text. */
    public record RerankCandidate(DiscoveryEntity entity, double baseScore, String topEvidence) {
    }

    /** Output: a candidate with its cross-encoder score. */
    public record RerankResult(RerankCandidate candidate, double rerankerScore) {
    }
}