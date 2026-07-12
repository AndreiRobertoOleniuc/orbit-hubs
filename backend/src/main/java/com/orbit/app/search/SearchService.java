package com.orbit.app.search;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.orbit.app.search.dto.SearchResponse;
import com.orbit.app.search.dto.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.app.embedding.EmbeddingKind;
import com.orbit.app.embedding.EmbeddingService;
import com.orbit.app.llm.LlmSummaryService;
import com.orbit.domain.entity.DiscoveryEntity;
import com.orbit.domain.repository.DiscoveryEntityRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

/**
 * Hybrid search service — combines full-text search (PostgreSQL FTS) with
 * semantic search (pgvector) via Reciprocal Rank Fusion (RRF).
 * <p>
 * Implements the retrieval stage from §6.1-6.2 of the reference doc:
 * <ol>
 *   <li>Embed the query with "query: " prefix</li>
 *   <li>Call {@code hybrid_search()} SQL function — two legs fused by RRF</li>
 *   <li>For each ranked entity, fetch top-k evidence chunks (deduped by source)</li>
 *   <li>Return entities with scores and evidence</li>
 * </ol>
 * <p>
 * No re-ranking (§6.3) or grounded generation (§6.4) yet — this is the
 * retrieval-only MVP to validate that the right person is found per query.
 */
@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    /**
     * Minimum evidence-chunk similarity for a result to be considered a good
     * match. Below this, the result is filtered out. If ALL results fall below,
     * {@code sufficient = false} and the frontend prompts the user to create an
     * Anfrage instead of showing weak matches (spec §4: "reicht die Suche
     * nicht"). Tunable via {@code orbit.search.similarity-threshold}.
     */
    @Value("${orbit.search.similarity-threshold:0.80}")
    private double similarityThreshold;

    private final EmbeddingService embeddingService;
    private final LlmSummaryService llmSummaryService;
    private final DiscoveryEntityRepository entityRepository;
    private final EntityManager em;

    public SearchService(EmbeddingService embeddingService,
                         LlmSummaryService llmSummaryService,
                         DiscoveryEntityRepository entityRepository,
                         EntityManager em) {
        this.embeddingService = embeddingService;
        this.llmSummaryService = llmSummaryService;
        this.entityRepository = entityRepository;
        this.em = em;
    }

    /**
     * Run a hybrid search query and return ranked entities with evidence.
     * <p>
     * Results whose best evidence-chunk similarity falls below
     * {@link #similarityThreshold} are filtered out. If no results survive the
     * threshold, {@link SearchResponse#sufficient()} is false — the frontend
     * should prompt the user to create an Anfrage (spec §4).
     *
     * @param queryText  the natural-language query (e.g. "Python web framework")
     * @param matchCount max entities to return (default 5)
     * @return search response with quality flag and filtered results
     */
    @Transactional(readOnly = true)
    public SearchResponse search(String queryText, int matchCount) {
        log.info("Search: \"{}\" (matchCount={}, threshold={})", queryText, matchCount, similarityThreshold);

        // 0. Translate query to English so FTS (English tsvector) and semantic
        //    (E5) both work without cross-lingual penalty. Gemini passes through
        //    English text unchanged; falls back to original if unavailable.
        String englishQuery = llmSummaryService.translateToEnglish(queryText);
        log.info("Search query normalized: \"{}\" → \"{}\"", queryText, englishQuery);

        // 1. Embed the query with "query: " prefix
        float[] queryEmbedding = embeddingService.embed(englishQuery, EmbeddingKind.QUERY);
        String vectorStr = toVectorString(queryEmbedding);

        // 2. Call hybrid_search function with the English query text
        Query searchQuery = em.createNativeQuery(
                "SELECT entity_id, score FROM hybrid_search(CAST(? AS text), ?::vector, CAST(? AS int), 1.0::float8, 1.0::float8, 50)");
        searchQuery.setParameter(1, englishQuery);
        searchQuery.setParameter(2, vectorStr);
        searchQuery.setParameter(3, matchCount);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = searchQuery.getResultList();

        if (rows.isEmpty()) {
            log.info("No results for \"{}\"", queryText);
            return new SearchResponse(queryText, false, List.of());
        }

        // 3. Build results with evidence chunks
        List<SearchResult> results = new ArrayList<>();
        for (Object[] row : rows) {
            Long entityId = ((Number) row[0]).longValue();
            double score = ((Number) row[1]).doubleValue();

            DiscoveryEntity entity = entityRepository.findById(entityId).orElse(null);
            if (entity == null) continue;

            List<SearchResult.EvidenceChunk> evidence = fetchEvidence(entityId, vectorStr, 3);
            results.add(new SearchResult(
                    entityId,
                    entity.getDisplayName(),
                    entity.getHeadline(),
                    entity.getOrgName(),
                    entity.getEntityType(),
                    score,
                    evidence
            ));
        }

        // 4. Quality threshold — filter out weak matches (spec §4: "reicht die
        //    Suche nicht"). Each result's quality is determined by its best
        //    evidence chunk similarity.
        List<SearchResult> strong = results.stream()
                .filter(r -> bestSimilarity(r) >= similarityThreshold)
                .sorted(Comparator.comparingDouble(SearchService::bestSimilarity).reversed())
                .toList();

        boolean sufficient = !strong.isEmpty();

        if (!sufficient) {
            log.info("Search \"{}\" → {} candidates but all below threshold {} (best: {})",
                    queryText, results.size(), similarityThreshold,
                    results.isEmpty() ? 0 : String.format("%.4f", bestSimilarity(results.get(0))));
        } else {
            log.info("Search \"{}\" → {} results above threshold (top: {} sim={})",
                    queryText, strong.size(),
                    strong.get(0).displayName(),
                    String.format("%.4f", bestSimilarity(strong.get(0))));
        }

        return new SearchResponse(queryText, sufficient, strong);
    }

    /**
     * Fetch the top-k best-matching chunks for an entity, deduped by source
     * (one chunk per source = one citation chip). Ordered by semantic similarity.
     */
    private List<SearchResult.EvidenceChunk> fetchEvidence(Long entityId, String vectorStr, int k) {
        Query evidenceQuery = em.createNativeQuery("""
                SELECT * FROM (
                  SELECT DISTINCT ON (s.id)
                         c.content, c.section, c.token_count,
                         s.label, s.source_url,
                         (c.embedding <#> ?::vector) AS distance
                  FROM chunks c
                  JOIN sources s ON c.source_id = s.id
                  WHERE c.entity_id = ?
                    AND c.embedding IS NOT NULL
                  ORDER BY s.id, c.embedding <#> ?::vector
                ) AS best_per_source
                ORDER BY distance
                LIMIT ?
                """);
        evidenceQuery.setParameter(1, vectorStr);
        evidenceQuery.setParameter(2, entityId);
        evidenceQuery.setParameter(3, vectorStr);
        evidenceQuery.setParameter(4, k);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = evidenceQuery.getResultList();

        List<SearchResult.EvidenceChunk> evidence = new ArrayList<>();
        for (Object[] row : rows) {
            String content = (String) row[0];
            String section = (String) row[1];
            int tokenCount = row[2] != null ? ((Number) row[2]).intValue() : 0;
            String label = (String) row[3];
            String sourceUrl = (String) row[4];
            double distance = ((Number) row[5]).doubleValue();
            double similarity = -distance; // <#> returns negative inner product

            evidence.add(new SearchResult.EvidenceChunk(
                    content, section, label, sourceUrl, tokenCount, similarity));
        }
        return evidence;
    }

    /**
     * Best (highest) evidence-chunk similarity for a result — the quality
     * signal used for threshold filtering.
     */
    private static double bestSimilarity(SearchResult result) {
        return result.evidence().stream()
                .mapToDouble(SearchResult.EvidenceChunk::similarity)
                .max()
                .orElse(0.0);
    }

    /**
     * Convert a float[] embedding to PostgreSQL vector string format: "[0.1,0.2,...]"
     */
    private static String toVectorString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(String.format("%.7f", embedding[i]));
        }
        sb.append(']');
        return sb.toString();
    }
}