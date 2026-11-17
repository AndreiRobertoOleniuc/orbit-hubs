package com.orbit.app.search;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.orbit.domain.entity.DiscoveryEntity;

/**
 * Cross-encoder reranker — the deterministic relevance gatekeeper.
 * <p>
 * Calls the configured reranker (by default cohere/rerank-5-fast on
 * OpenRouter), which scores (query, passage) pairs in [0, 1]. A candidate is
 * scored on one or more passages — its profile card and, optionally, its best
 * evidence chunks — and takes the score of the best one. The card (name,
 * headline, tags, summary, start of the best chunk) carries the profile as a
 * whole; a chunk carries one topic on its own. By default only the card is
 * scored ({@code orbit.search.rerank-passages=0}).
 * Candidates below {@link #relevanceThreshold} are dropped. Scores of
 * different reranker models are not comparable, so the threshold belongs to
 * the model: 0.3 for cohere/rerank-5-fast (evaluation-v3, 2026-11-16); another
 * model needs its own run.
 * <p>
 * Two-stage retrieval:
 * <ol>
 *   <li>Stage 1 — bi-encoder + keyword retrieval, fused by RRF, in {@link SearchService}</li>
 *   <li>Stage 2 — cross-encoder reranking (this service)</li>
 * </ol>
 * The LLM only writes the German rationale for results that passed;
 * it never decides who passes.
 */
@Service
public class CrossEncoderReranker {

    private static final Logger log = LoggerFactory.getLogger(CrossEncoderReranker.class);

    /** Longest passage when the caller sets no limit. */
    static final int MAX_PASSAGE_CHARS = 2000;

    /** How a source type is named in a passage header. */
    private static final Map<String, String> SOURCE_TYPE_NAMES = Map.of(
            "orcid_pub", "Publication",
            "github", "GitHub repository",
            "company_site", "Company website",
            "personal_site", "Personal website",
            "linkedin_pdf", "CV",
            "zefix", "Commercial register",
            "manual_text", "Profile text");

    private final CrossEncoderClient rerankerClient;
    private final double relevanceThreshold;

    public CrossEncoderReranker(
            CrossEncoderClient rerankerClient,
            @Value("${orbit.search.reranker-threshold}") double relevanceThreshold) {
        this.rerankerClient = rerankerClient;
        this.relevanceThreshold = relevanceThreshold;
        log.info("Cross-encoder reranker: provider={}, threshold={}",
                rerankerClient.getClass().getSimpleName(), relevanceThreshold);
    }

    /**
     * Score and filter candidates: those whose best passage reaches the
     * threshold, sorted by score descending. Provider failures propagate so the
     * API can return 503 rather than display results that did not pass the
     * cross-encoder.
     */
    public List<RerankResult> rerank(String query, List<RerankCandidate> candidates) {
        return keepRelevant(score(query, candidates));
    }

    /**
     * Score every candidate without filtering, in one provider call for all
     * passages. Results are in input order, one per candidate; a candidate
     * without passages scores 0.
     */
    public List<RerankResult> score(String query, List<RerankCandidate> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<String> documents = new ArrayList<>();
        for (RerankCandidate candidate : candidates) {
            for (RerankPassage passage : candidate.passages()) documents.add(passage.text());
        }
        long start = System.nanoTime();
        List<Double> scores = documents.isEmpty() ? List.of() : rerankerClient.score(query, documents);
        // A hosted cross-encoder's time grows with the characters it reads; this
        // line is what to look at when a search is slow.
        log.info("Reranker scored {} passages of {} candidates ({} chars) in {} ms",
                documents.size(), candidates.size(), documents.stream().mapToInt(String::length).sum(),
                (System.nanoTime() - start) / 1_000_000);
        if (scores.size() != documents.size()) {
            throw new IllegalStateException("Cross-encoder returned the wrong number of scores");
        }

        List<RerankResult> scored = new ArrayList<>(candidates.size());
        int next = 0;
        for (RerankCandidate candidate : candidates) {
            double best = 0.0;
            RerankPassage bestPassage = null;
            for (RerankPassage passage : candidate.passages()) {
                double score = scores.get(next++);
                if (bestPassage == null || score > best) {
                    best = score;
                    bestPassage = passage;
                }
            }
            scored.add(new RerankResult(candidate, best, bestPassage));
        }
        return scored;
    }

    /**
     * Apply the relevance threshold to already-scored candidates: keep those at
     * or above it, sorted by score descending (stable, so ties keep input order).
     */
    public List<RerankResult> keepRelevant(List<RerankResult> scored) {
        if (scored.isEmpty()) {
            return List.of();
        }
        List<RerankResult> filtered = new ArrayList<>();
        for (RerankResult result : scored) {
            if (result.rerankerScore() >= relevanceThreshold) filtered.add(result);
        }
        filtered.sort(Comparator.comparingDouble(RerankResult::rerankerScore).reversed());
        log.info("Reranker: {} candidates → {} passed threshold {} (top scores: {})",
                scored.size(), filtered.size(), relevanceThreshold,
                filtered.stream().limit(10).map(r -> String.format("%.3f", r.rerankerScore())).toList());
        return filtered;
    }

    /** The relevance threshold in force (reported in the search trace). */
    public double relevanceThreshold() {
        return relevanceThreshold;
    }

    /**
     * An evidence chunk as the cross-encoder sees it: a short header naming who
     * it belongs to and what kind of source it is, then the chunk. Without the
     * header a publication abstract says nothing about the person or company
     * behind it.
     */
    static RerankPassage evidencePassage(DiscoveryEntity entity, String sourceType, String label, String content) {
        return evidencePassage(entity, sourceType, label, content, MAX_PASSAGE_CHARS);
    }

    /** {@link #evidencePassage(DiscoveryEntity, String, String, String)} cut to {@code maxChars}. */
    static RerankPassage evidencePassage(DiscoveryEntity entity, String sourceType, String label, String content,
                                         int maxChars) {
        StringBuilder text = new StringBuilder(entity.getDisplayName());
        if (entity.getHeadline() != null && !entity.getHeadline().isBlank()) {
            text.append(" — ").append(entity.getHeadline().trim());
        }
        text.append('\n').append(SOURCE_TYPE_NAMES.getOrDefault(sourceType, "Source"));
        if (label != null && !label.isBlank()) text.append(": ").append(label.trim());
        text.append("\n\n").append(content == null ? "" : content);
        return new RerankPassage(label, truncate(text.toString(), maxChars));
    }

    /**
     * The profile card: name, headline, tags, the first sentences of the
     * summary and the start of the best evidence chunk.
     */
    static RerankPassage cardPassage(DiscoveryEntity entity, List<String> tags, String topEvidence) {
        return cardPassage(entity, tags, topEvidence, MAX_PASSAGE_CHARS);
    }

    /** {@link #cardPassage(DiscoveryEntity, List, String)} cut to {@code maxChars}. */
    static RerankPassage cardPassage(DiscoveryEntity entity, List<String> tags, String topEvidence, int maxChars) {
        StringBuilder text = new StringBuilder(entity.getDisplayName());
        if (entity.getHeadline() != null && !entity.getHeadline().isBlank()) {
            text.append(": ").append(entity.getHeadline());
        }
        if (tags != null && !tags.isEmpty()) {
            text.append(". Expertise: ").append(String.join(", ", tags));
        }
        if (entity.getSummary() != null && !entity.getSummary().isBlank()) {
            text.append(". ").append(leadingSentences(entity.getSummary()));
        }
        if (topEvidence != null && !topEvidence.isBlank()) {
            text.append(" Matching evidence: ").append(truncate(topEvidence, 500));
        }
        return new RerankPassage(RerankPassage.CARD_LABEL, truncate(text.toString(), maxChars));
    }

    /**
     * The first 3 sentences of the summary, at most 300 characters. They hold
     * the core expertise statement; later sentences often list publications or
     * side topics that lower the score (0.12 lower for multi-topic researchers).
     */
    private static String leadingSentences(String summary) {
        String[] sentences = summary.split("(?<=[.!?])\\s+");
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < Math.min(3, sentences.length); i++) {
            if (i > 0) text.append(' ');
            text.append(sentences[i].trim());
        }
        return truncate(text.toString(), 300);
    }

    private static String truncate(String value, int maxLength) {
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
