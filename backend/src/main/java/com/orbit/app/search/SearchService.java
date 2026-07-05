package com.orbit.app.search;

import java.util.ArrayList;
import java.util.List;

import com.orbit.app.search.dto.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.app.embedding.EmbeddingKind;
import com.orbit.app.embedding.EmbeddingService;
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

    private final EmbeddingService embeddingService;
    private final DiscoveryEntityRepository entityRepository;
    private final EntityManager em;

    public SearchService(EmbeddingService embeddingService,
                         DiscoveryEntityRepository entityRepository,
                         EntityManager em) {
        this.embeddingService = embeddingService;
        this.entityRepository = entityRepository;
        this.em = em;
    }

    /**
     * Run a hybrid search query and return ranked entities with evidence.
     *
     * @param queryText  the natural-language query (e.g. "Python web framework")
     * @param matchCount max entities to return (default 5)
     * @return ranked search results with evidence chunks
     */
    @Transactional(readOnly = true)
    public List<SearchResult> search(String queryText, int matchCount) {
        log.info("Search: \"{}\" (matchCount={})", queryText, matchCount);

        // 1. Embed the query with "query: " prefix
        float[] queryEmbedding = embeddingService.embed(queryText, EmbeddingKind.QUERY);
        String vectorStr = toVectorString(queryEmbedding);

        // 2. Call hybrid_search function (explicit casts: Hibernate sends varchar/numeric, function expects text/float)
        Query searchQuery = em.createNativeQuery(
                "SELECT entity_id, score FROM hybrid_search(CAST(? AS text), ?::vector, CAST(? AS int), 1.0::float8, 1.0::float8, 50)");
        searchQuery.setParameter(1, queryText);
        searchQuery.setParameter(2, vectorStr);
        searchQuery.setParameter(3, matchCount);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = searchQuery.getResultList();

        if (rows.isEmpty()) {
            log.info("No results for \"{}\"", queryText);
            return List.of();
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

        log.info("Search \"{}\" → {} results (top: {} score={})",
                queryText, results.size(),
                results.isEmpty() ? "none" : results.get(0).displayName(),
                results.isEmpty() ? 0 : String.format("%.6f", results.get(0).score()));

        return results;
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