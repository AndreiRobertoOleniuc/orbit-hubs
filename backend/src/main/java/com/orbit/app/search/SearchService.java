package com.orbit.app.search;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import com.orbit.app.controllers.dto.EntityDto;
import com.orbit.app.llm.dto.MatchRationale;
import com.orbit.app.llm.dto.RationaleClaim;
import com.orbit.app.llm.dto.RationaleEvidence;
import com.orbit.app.profile.EntityService;
import com.orbit.app.search.dto.Citation;
import com.orbit.app.search.dto.ResolvedScope;
import com.orbit.app.search.dto.ScopeSelection;
import com.orbit.app.search.dto.SearchRequest;
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
import com.orbit.domain.entity.Hub;
import com.orbit.domain.repository.DiscoveryEntityRepository;
import com.orbit.domain.repository.HubRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

/**
 * Hybrid search service — the full discovery pipeline (reference §6):
 * retrieve → re-rank → ground.
 * <ol>
 *   <li>Translate + embed the query ("query: " prefix)</li>
 *   <li>Resolve the scope selection and call the scoped {@code hybrid_search}
 *       SQL function (FTS + pgvector fused by RRF, hard-filtered by Hub/Region §5)</li>
 *   <li>Fetch top evidence chunks per entity (deduped by source)</li>
 *   <li>Re-rank by the soft signals — availability overlap + open boost (§6.3)</li>
 *   <li>Generate a grounded German rationale + validated citations for the top
 *       results (§6.4/§6.5); degrade to an evidence-only card when the LLM is off</li>
 * </ol>
 */
@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    /** Evidence chunks fetched per entity (grounding material; chips are the cited subset). */
    private static final int EVIDENCE_K = 4;

    /**
     * Minimum best-evidence similarity for a result to count as a good match.
     * Below this, the result is filtered out. If ALL results fall below,
     * {@code sufficient = false} and the frontend prompts the user to widen the
     * scope or create an Anfrage (spec §4). Tunable via
     * {@code orbit.search.similarity-threshold}.
     */
    @Value("${orbit.search.similarity-threshold:0.80}")
    private double similarityThreshold;

    /** How many top results get a grounded Gemini rationale — bounds cost (§6.4). */
    @Value("${orbit.search.rationale-top-n:8}")
    private int rationaleTopN;

    private final EmbeddingService embeddingService;
    private final LlmSummaryService llmSummaryService;
    private final DiscoveryEntityRepository entityRepository;
    private final HubRepository hubRepository;
    private final ScopeResolver scopeResolver;
    private final RerankService rerankService;
    private final EntityService entityService;
    private final EntityManager em;

    public SearchService(EmbeddingService embeddingService,
                         LlmSummaryService llmSummaryService,
                         DiscoveryEntityRepository entityRepository,
                         HubRepository hubRepository,
                         ScopeResolver scopeResolver,
                         RerankService rerankService,
                         EntityService entityService,
                         EntityManager em) {
        this.embeddingService = embeddingService;
        this.llmSummaryService = llmSummaryService;
        this.entityRepository = entityRepository;
        this.hubRepository = hubRepository;
        this.scopeResolver = scopeResolver;
        this.rerankService = rerankService;
        this.entityService = entityService;
        this.em = em;
    }

    /**
     * Run the discovery pipeline for a request and return ranked, grounded results.
     * Results whose best evidence similarity is below the threshold are dropped;
     * when none survive, {@link SearchResponse#sufficient()} is false.
     */
    @Transactional(readOnly = true)
    public SearchResponse search(SearchRequest request) {
        String queryText = request.query();
        if (queryText == null || queryText.isBlank()) {
            return new SearchResponse("", false, List.of());
        }
        int matchCount = request.matchCount() != null ? request.matchCount() : 10;
        ResolvedScope scope = scopeResolver.resolve(request.scope());
        log.info("Search: \"{}\" (matchCount={}, scope={}, threshold={})",
                queryText, matchCount, describeScope(request.scope()), similarityThreshold);

        // 1. Translate + embed the query (English FTS + E5 semantic both work).
        String englishQuery = llmSummaryService.translateToEnglish(queryText);
        float[] queryEmbedding = embeddingService.embed(englishQuery, EmbeddingKind.QUERY);
        String vectorStr = toVectorString(queryEmbedding);

        // 2. Scoped hybrid_search — HARD FILTER (§5) applied inside the SQL.
        Query searchQuery = em.createNativeQuery("""
                SELECT entity_id, score FROM hybrid_search(
                  CAST(? AS text), ?::vector,
                  CAST(? AS bigint[]), CAST(? AS bigint[]),
                  CAST(? AS int), 1.0::float8, 1.0::float8, 50)
                """);
        searchQuery.setParameter(1, englishQuery);
        searchQuery.setParameter(2, vectorStr);
        searchQuery.setParameter(3, toPgArray(scope.hubIds()));
        searchQuery.setParameter(4, toPgArray(scope.regionIds()));
        searchQuery.setParameter(5, matchCount);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = searchQuery.getResultList();
        if (rows.isEmpty()) {
            log.info("No results for \"{}\"", queryText);
            return new SearchResponse(queryText, false, List.of());
        }

        // 3. Load entities + evidence.
        List<Candidate> candidates = new ArrayList<>();
        for (Object[] row : rows) {
            Long entityId = ((Number) row[0]).longValue();
            double base = ((Number) row[1]).doubleValue();
            DiscoveryEntity entity = entityRepository.findById(entityId).orElse(null);
            if (entity == null) continue;
            List<Evidence> evidence = fetchEvidence(entityId, vectorStr, EVIDENCE_K);
            candidates.add(new Candidate(entity, base, evidence));
        }
        if (candidates.isEmpty()) {
            return new SearchResponse(queryText, false, List.of());
        }

        // 4. Re-rank (soft signals) and keep only above-threshold matches.
        List<Double> baseScores = candidates.stream().map(c -> c.base).toList();
        List<DiscoveryEntity> ents = candidates.stream().map(c -> c.entity).toList();
        double[] finals = rerankService.finalScores(
                baseScores, ents, request.wantFrom(), request.wantTo(), request.wantPensumPct());
        for (int i = 0; i < candidates.size(); i++) candidates.get(i).finalScore = finals[i];

        List<Candidate> strong = candidates.stream()
                .filter(c -> c.bestSimilarity() >= similarityThreshold)
                .sorted(Comparator.comparingDouble((Candidate c) -> c.finalScore).reversed())
                .toList();

        if (strong.isEmpty()) {
            log.info("Search \"{}\" → {} candidates, none above threshold {}",
                    queryText, candidates.size(), similarityThreshold);
            return new SearchResponse(queryText, false, List.of());
        }

        // 5. Ground the shown results + build cards.
        //    Parallelize the LLM grounding calls — each is an independent Gemini
        //    request, so N results ground in ~1 call's time instead of N×.
        Hub anchorHub = resolveAnchorHub(request.scope());
        List<CompletableFuture<Grounded>> groundFutures = new ArrayList<>();
        for (int i = 0; i < strong.size(); i++) {
            final Candidate c = strong.get(i);
            if (i < rationaleTopN) {
                groundFutures.add(CompletableFuture.supplyAsync(() -> ground(queryText, c)));
            } else {
                groundFutures.add(CompletableFuture.completedFuture(
                        new Grounded(null, fallbackCitations(c), true)));
            }
        }

        List<SearchResult> results = new ArrayList<>();
        for (int i = 0; i < groundFutures.size(); i++) {
            Grounded grounded = groundFutures.get(i).join();
            Candidate c = strong.get(i);

            // LLM relevance filter: if the grounding says this match isn't relevant
            // to the actual query, drop it. This catches generic-keyword false
            // positives (e.g. "AI Engineer for RAG" matching HCI/UX papers).
            if (!grounded.relevant()) {
                log.info("Search \"{}\" → dropped {} (LLM says not relevant)",
                        queryText, c.entity.getDisplayName());
                continue;
            }

            EntityDto dto = entityService.toDto(c.entity);
            results.add(new SearchResult(dto, c.finalScore, grounded.rationale(),
                    grounded.citations(), distanceLabel(anchorHub, c.entity)));
        }

        if (results.isEmpty()) {
            log.info("Search \"{}\" → {} candidates, all filtered by LLM relevance",
                    queryText, strong.size());
            return new SearchResponse(queryText, false, List.of());
        }

        log.info("Search \"{}\" → {} results (top: {})",
                queryText, results.size(), strong.get(0).entity.getDisplayName());
        return new SearchResponse(queryText, true, results);
    }

    // ─── Grounding (§6.4 / §6.5) ───

    /**
     * Generate a grounded rationale and validate its citations. Keeps only claims
     * whose citations are all in the evidence set; a shown rationale always has
     * ≥1 validated citation. Degrades to evidence-only (rationale = null) when the
     * LLM is unavailable or nothing survives validation.
     */
    private Grounded ground(String queryText, Candidate c) {
        List<Evidence> evidence = c.evidence;
        if (evidence.isEmpty()) return new Grounded(null, List.of(), true);

        // Assign stable citation ids S1, S2, … (one per source — already deduped).
        List<RationaleEvidence> forLlm = new ArrayList<>();
        for (int i = 0; i < evidence.size(); i++) {
            Evidence ev = evidence.get(i);
            forLlm.add(new RationaleEvidence("S" + (i + 1), ev.sourceType(), ev.label(), ev.content()));
        }

        MatchRationale out = llmSummaryService.generateMatchRationale(
                queryText, c.entity.getDisplayName(), c.entity.getHeadline(), c.entity.getOrgName(), forLlm);
        if (out == null) {
            return new Grounded(null, fallbackCitations(c), true);   // LLM off → evidence-only, keep
        }

        // §6.5 — drop claims citing ids not in the evidence set; recompute cited set.
        Set<String> allowed = Set.copyOf(forLlm.stream().map(RationaleEvidence::id).toList());
        Set<String> cited = out.claims() == null ? Set.of()
                : out.claims().stream()
                        .filter(claim -> allowed.containsAll(claim.sourceIds()))
                        .map(RationaleClaim::sourceIds)
                        .flatMap(List::stream)
                        .collect(Collectors.toSet());

        List<Citation> chips = new ArrayList<>();
        for (int i = 0; i < evidence.size(); i++) {
            if (cited.contains("S" + (i + 1))) {
                Evidence ev = evidence.get(i);
                chips.add(new Citation(ev.sourceId(), ev.label(), ev.sourceType(), ev.sourceUrl()));
            }
        }
        if (chips.isEmpty()) {
            // LLM returned but no claims had valid citations — use fallback
            // citations but respect the LLM's relevance judgement.
            return new Grounded(null, fallbackCitations(c), out.relevant());
        }
        return new Grounded(out.rationale(), chips, out.relevant());
    }

    /** All top evidence sources as chips (used when there is no grounded rationale). */
    private List<Citation> fallbackCitations(Candidate c) {
        return c.evidence.stream()
                .map(ev -> new Citation(ev.sourceId(), ev.label(), ev.sourceType(), ev.sourceUrl()))
                .toList();
    }

    // ─── Evidence ───

    /**
     * Fetch the top-k best-matching chunks for an entity, deduped by source (one
     * chunk per source = one candidate chip). Ordered by semantic similarity.
     */
    private List<Evidence> fetchEvidence(Long entityId, String vectorStr, int k) {
        Query evidenceQuery = em.createNativeQuery("""
                SELECT * FROM (
                  SELECT DISTINCT ON (s.id)
                         s.id AS source_id, s.source_type, s.label, s.source_url,
                         c.content, (c.embedding <#> ?::vector) AS distance
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

        List<Evidence> evidence = new ArrayList<>();
        for (Object[] row : rows) {
            Long sourceId = row[0] != null ? ((Number) row[0]).longValue() : null;
            String sourceType = (String) row[1];
            String label = (String) row[2];
            String sourceUrl = (String) row[3];
            String content = (String) row[4];
            double distance = ((Number) row[5]).doubleValue();
            evidence.add(new Evidence(sourceId, sourceType, label, sourceUrl, content, -distance));
        }
        return evidence;
    }

    // ─── Distance ───

    private Hub resolveAnchorHub(ScopeSelection scope) {
        if (scope == null || scope.kind() == null || !"hub".equalsIgnoreCase(scope.kind())
                || scope.hubId() == null) {
            return null;
        }
        return hubRepository.findById(scope.hubId()).orElse(null);
    }

    /** Best-effort "≈ N km von {hub}" when both the hub anchor and entity have coordinates. */
    private String distanceLabel(Hub anchorHub, DiscoveryEntity entity) {
        if (anchorHub == null || anchorHub.getLatitude() == null || anchorHub.getLongitude() == null
                || entity.getLatitude() == null || entity.getLongitude() == null) {
            return null;
        }
        double km = haversineKm(anchorHub.getLatitude(), anchorHub.getLongitude(),
                entity.getLatitude(), entity.getLongitude());
        return "≈ " + Math.round(km) + " km von " + anchorHub.getName();
    }

    private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    // ─── Helpers ───

    private static String describeScope(ScopeSelection scope) {
        if (scope == null || scope.kind() == null) return "all";
        return scope.kind() + (scope.hubId() != null ? ":hub" + scope.hubId() : "")
                + (scope.regionId() != null ? ":region" + scope.regionId() : "");
    }

    /** Postgres array literal: {@code {}} for empty, else {@code {1,2,3}}. */
    private static String toPgArray(long[] ids) {
        if (ids.length == 0) return "{}";
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < ids.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(ids[i]);
        }
        return sb.append('}').toString();
    }

    private static String toVectorString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(String.format("%.7f", embedding[i]));
        }
        return sb.append(']').toString();
    }

    // ─── Internal holders ───

    /** A retrieved candidate with its base RRF score, evidence, and re-rank score. */
    private static final class Candidate {
        final DiscoveryEntity entity;
        final double base;
        final List<Evidence> evidence;
        double finalScore;

        Candidate(DiscoveryEntity entity, double base, List<Evidence> evidence) {
            this.entity = entity;
            this.base = base;
            this.evidence = evidence;
        }

        double bestSimilarity() {
            return evidence.stream().mapToDouble(Evidence::similarity).max().orElse(0.0);
        }
    }

    /** A winning chunk's source + content, for chips and grounding. */
    private record Evidence(Long sourceId, String sourceType, String label,
                            String sourceUrl, String content, double similarity) {
    }

    /** Grounding output: rationale (nullable) + the chips to render. */
    private record Grounded(String rationale, List<Citation> citations, boolean relevant) {
    }
}
