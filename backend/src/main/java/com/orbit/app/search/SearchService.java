package com.orbit.app.search;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.orbit.app.admin.AdminAccess;
import com.orbit.app.auth.CurrentUser;
import com.orbit.app.controllers.dto.EntityDto;
import com.orbit.app.llm.dto.MatchRationale;
import com.orbit.app.llm.dto.RationaleClaim;
import com.orbit.app.llm.dto.RationaleEvidence;
import com.orbit.app.llm.dto.SearchQueryRewrite;
import com.orbit.app.profile.EntityService;
import com.orbit.app.search.dto.CandidateTrace;
import com.orbit.app.search.dto.Citation;
import com.orbit.app.search.dto.ResolvedScope;
import com.orbit.app.search.dto.ScopeSelection;
import com.orbit.app.search.dto.SearchRequest;
import com.orbit.app.search.dto.SearchResponse;
import com.orbit.app.search.dto.SearchRationale;
import com.orbit.app.search.dto.SearchRationaleRequest;
import com.orbit.app.search.dto.SearchResult;
import com.orbit.app.search.dto.SearchTrace;
import com.orbit.app.search.dto.SemanticEntityRank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.orbit.app.embedding.EmbeddingKind;
import com.orbit.app.embedding.EmbeddingService;
import com.orbit.app.inference.InferenceDeadline;
import com.orbit.app.llm.LlmSummaryService;
import com.orbit.domain.entity.DiscoveryEntity;
import com.orbit.domain.entity.Hub;
import com.orbit.domain.repository.DiscoveryEntityRepository;
import com.orbit.domain.repository.HubRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

/**
 * Hybrid search service — the full discovery pipeline (reference §6):
 * retrieve → cross-encoder rerank → soft re-rank → ground.
 * <p>
 * Industry-standard 2-stage retrieval:
 * <ol>
 *   <li><b>Stage 1 — Bi-encoder retrieval:</b> Translate + embed the query,
 *       call the scoped {@code hybrid_search} SQL function (FTS + pgvector
 *       fused by RRF, hard-filtered by Hub/Region §5). Fetch top evidence
 *       chunks per entity.</li>
 *   <li><b>Stage 2 — Cross-encoder reranking:</b> A deterministic
 *       reranker (by default cohere/rerank-5-fast on OpenRouter) scores the best-retrieved
 *       candidates on their own passages (profile card + best evidence
 *       chunk) and drops irrelevant ones. It is the only relevance gate.</li>
 *   <li><b>Soft re-rank:</b> Multiplicative boost on the cross-encoder score
 *       using availability overlap + openToRequests (§6.3). Runs AFTER the
 *       cross-encoder so soft signals fine-tune the order of relevant
 *       candidates, not determine who passes the relevance filter.
 *       {@code combined = ceScore × (1 + W·avail + W·open)}.</li>
 *   <li><b>Explanation layer:</b> per result, on request ({@link #explain}),
 *       a grounded German rationale + validated citations (§6.4/§6.5) from
 *       the LLM. The LLM only <em>explains</em> the match — it never decides who
 *       gets in. Degrades to an evidence-only card when the LLM is off.</li>
 * </ol>
 */
@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    /** Evidence chunks fetched per entity (grounding material; chips are the cited subset). */
    private static final int EVIDENCE_K = 4;

    /**
     * How many of the best-retrieved candidates the cross-encoder scores. The
     * rest cannot be shown. Bounds the cost of stage 2: each costs
     * {@link #rerankPassages} + 1 scored pairs. The reranker on OpenRouter
     * scores 30 cards in about 0.35 s and 50 in about 0.4 s (2026-11-16), so
     * depth costs little time there; a model that scores pairs one after
     * another (bge-reranker-v2-m3 on Hugging Face: 0.15 s per pair) needs a
     * smaller one. With today's 56 profiles 20, 30 and 50 found the same
     * expected profiles; the margin is for a larger corpus.
     */
    @Value("${orbit.search.rerank-depth:30}")
    private int rerankDepth = 30;

    /**
     * Evidence chunks per candidate the cross-encoder scores, besides the
     * profile card. None by default: the card already carries the start of the
     * best chunk, and the relevance threshold is calibrated for the card alone.
     * With bge-reranker-v2-m3 (2026-11-16) the card alone found as many
     * expected profiles as card + chunk, with fewer irrelevant results, and
     * 2 or 3 chunks let a negative control through: more passages give an
     * irrelevant candidate more chances to score high by luck. Not measured
     * again with the current reranker.
     */
    @Value("${orbit.search.rerank-passages:0}")
    private int rerankPassages = 0;

    /**
     * Longest passage the cross-encoder is given, in characters. Together with
     * {@link #rerankDepth} and {@link #rerankPassages} it decides how much text
     * stage 2 reads, which is what a hosted reranker bills and takes time for.
     */
    @Value("${orbit.search.rerank-passage-chars:800}")
    private int rerankPassageChars = 800;

    /**
     * When true, any caller may request the {@link SearchTrace} with
     * {@code debug=true}. Off by default: the trace is then admin-only. Meant for
     * local evaluation runs against a snapshot, never for a public deployment.
     */
    @Value("${orbit.search.trace-open:false}")
    private boolean traceOpen;

    /**
     * How many entities stage 1 retrieves, independent of how many results are
     * shown. Candidate recall is what the later stages can never recover, so this
     * is deliberately much larger than a page of results.
     */
    @Value("${orbit.search.candidate-depth:50}")
    private int candidateDepth = 50;

    /** RRF weight of the keyword leg; 0 switches it off. */
    @Value("${orbit.search.retrieval.lexical-weight:1.0}")
    private double lexicalWeight = 1.0;

    /** RRF weight of the semantic leg on the English query; 0 switches it off. */
    @Value("${orbit.search.retrieval.semantic-weight:1.0}")
    private double semanticWeight = 1.0;

    /**
     * RRF weight of the semantic leg on the query as typed (multilingual e5);
     * 0 switches it off. Unused when the query is already English.
     */
    @Value("${orbit.search.retrieval.original-language-weight:1.0}")
    private double originalLanguageWeight = 1.0;

    /**
     * How long the hosted embedding and reranker calls of one search may take
     * in all, retries included. Past it the search fails with HTTP 503 rather
     * than keep the user waiting on a model that is loading or rate-limited.
     */
    @Value("${orbit.search.inference-budget:PT30S}")
    private Duration inferenceBudget = Duration.ofSeconds(30);

    /**
     * Runs the query embedding that overlaps the LLM rewrite. Virtual threads:
     * the work is waiting on a hosted model, and the common pool has only a
     * few threads on a small machine.
     */
    private static final Executor ASYNC = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * Chunks the semantic leg ranks before collapsing them to entities. Must stay
     * well above the chunks of the candidates: in the 2026-11-15 snapshot one
     * expected company's best chunk ranked 581st behind prolific researchers.
     */
    private static final int CHUNK_POOL = 2000;

    /** RRF smoothing constant, the common default. */
    private static final int RRF_K = 60;

    /**
     * Role words the condense prompt adds to every query ("…, expert"). They say
     * nothing about the topic, so the keyword leg leaves them out.
     */
    private static final Pattern ROLE_WORDS = Pattern.compile(
            "\\b(experts?|expertise|specialists?|researchers?|consultants?|professors?)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * Stage 1: entity candidates from the scoped {@code hybrid_search} (V19).
     * Parameters: English vector, original-language vector, keywords, hub ids,
     * region ids, candidate depth, chunk pool, lexical / semantic /
     * original-language weights, RRF k.
     * <p>
     * The vectors are parsed once, in a materialized CTE. Passed straight into
     * the (inlined) SQL function, a bound {@code CAST(? AS vector)} is re-parsed
     * for every chunk once the driver switches to a generic plan: 116 ms instead
     * of 8 ms on 723 chunks.
     */
    static final String HYBRID_SEARCH_SQL = """
            WITH q AS MATERIALIZED (
              SELECT CAST(? AS vector) AS english, CAST(? AS vector) AS original
            )
            SELECT h.entity_id, h.score, h.lexical_rank, h.semantic_rank, h.alt_semantic_rank
            FROM q CROSS JOIN LATERAL hybrid_search(
              CAST(? AS text), q.english, q.original,
              CAST(? AS bigint[]), CAST(? AS bigint[]),
              CAST(? AS int), CAST(? AS int),
              CAST(? AS float8), CAST(? AS float8), CAST(? AS float8), CAST(? AS int)) h
            """;

    /** How many entities the trace's entity-collapsed semantic ranking lists. */
    private static final int SEMANTIC_ENTITY_RANKING_DEPTH = 50;

    /**
     * Trace-only diagnostic: rank every scoped, active chunk by similarity and
     * keep each entity's best rank. Mirrors the scope + active-source filter of
     * {@code hybrid_search}, but collapses to the entity before limiting.
     * Parameters: vector, vector, hub ids, region ids, region ids, hub ids, limit.
     */
    static final String SEMANTIC_ENTITY_RANKING_SQL = """
            SELECT r.entity_id, e.display_name, r.best_rank, r.best_similarity
            FROM (
              SELECT ranked.entity_id,
                     min(ranked.rank_ix) AS best_rank,
                     max(ranked.similarity) AS best_similarity
              FROM (
                SELECT c.entity_id,
                       row_number() OVER (ORDER BY c.embedding <#> ?::vector) AS rank_ix,
                       -(c.embedding <#> ?::vector) AS similarity
                FROM chunks c
                JOIN sources src ON src.id = c.source_id AND src.active
                JOIN entities en ON en.id = c.entity_id
                WHERE c.embedding IS NOT NULL
                  AND en.status = 'active'
                  AND (
                    (cardinality(CAST(? AS bigint[])) = 0 AND cardinality(CAST(? AS bigint[])) = 0)
                    OR en.home_region_id = ANY(CAST(? AS bigint[]))
                    OR EXISTS (SELECT 1 FROM hub_memberships m
                               WHERE m.entity_id = en.id AND m.hub_id = ANY(CAST(? AS bigint[])))
                  )
              ) ranked
              GROUP BY ranked.entity_id
            ) r
            JOIN entities e ON e.id = r.entity_id
            ORDER BY r.best_rank
            LIMIT ?
            """;

    /**
     * Evidence for a whole candidate list in one round trip: per entity, the best
     * chunk of each active source, then the closest {@code k} of those.
     * Parameters: vector, entity ids, k. The vector is parsed once (see
     * {@link #HYBRID_SEARCH_SQL}).
     */
    static final String EVIDENCE_SQL = """
            WITH q AS MATERIALIZED (SELECT CAST(? AS vector) AS v)
            SELECT entity_id, source_id, source_type, label, source_url, content, distance
            FROM (
              SELECT best.*,
                     row_number() OVER (PARTITION BY best.entity_id ORDER BY best.distance) AS source_rank
              FROM (
                SELECT DISTINCT ON (c.entity_id, s.id)
                       c.entity_id, s.id AS source_id, s.source_type, s.label, s.source_url,
                       c.content, (c.embedding <#> q.v) AS distance
                FROM q
                CROSS JOIN chunks c
                JOIN sources s ON c.source_id = s.id
                WHERE c.entity_id = ANY(CAST(? AS bigint[]))
                  AND c.embedding IS NOT NULL
                  AND s.active
                ORDER BY c.entity_id, s.id, c.embedding <#> q.v
              ) AS best
            ) AS ranked
            WHERE source_rank <= ?
            ORDER BY entity_id, distance
            """;

    /**
     * Query vectors of recent searches by query text (as typed and in English),
     * so a repeated search and a rationale request need no new embedding call.
     */
    private static final int QUERY_VECTOR_CACHE_SIZE = 500;

    private final Map<String, String> queryVectors = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > QUERY_VECTOR_CACHE_SIZE;
                }
            });

    /**
     * The pgvector literal of a query text: from the cache, or embedded now
     * within {@code deadlineNanos}. Not {@code computeIfAbsent}, which would
     * hold the map's lock through the embedding call.
     */
    private String queryVector(String text, long deadlineNanos) {
        String cached = queryVectors.get(text);
        if (cached != null) return cached;
        String vector = toVectorString(InferenceDeadline.within(deadlineNanos,
                () -> embeddingService.embed(text, EmbeddingKind.QUERY)));
        queryVectors.put(text, vector);
        return vector;
    }

    /** Tag labels for a whole candidate list. Parameter: entity ids. */
    static final String COMPETENCE_TAGS_SQL = """
            SELECT et.entity_id, t.label
            FROM entity_tags et
            JOIN tags t ON t.id = et.tag_id
            WHERE et.entity_id = ANY(CAST(? AS bigint[]))
            ORDER BY et.entity_id, t.label
            """;

    private final EmbeddingService embeddingService;
    private final SearchQueryRewriter queryRewriter;
    private final LlmSummaryService llmSummaryService;
    private final DiscoveryEntityRepository entityRepository;
    private final HubRepository hubRepository;
    private final ScopeResolver scopeResolver;
    private final RerankService rerankService;
    private final CrossEncoderReranker crossEncoderReranker;
    private final EntityService entityService;
    private final AdminAccess adminAccess;
    private final CurrentUser currentUser;
    private final EntityManager em;

    public SearchService(EmbeddingService embeddingService,
                         SearchQueryRewriter queryRewriter,
                         LlmSummaryService llmSummaryService,
                         DiscoveryEntityRepository entityRepository,
                         HubRepository hubRepository,
                         ScopeResolver scopeResolver,
                         RerankService rerankService,
                         CrossEncoderReranker crossEncoderReranker,
                         EntityService entityService,
                         AdminAccess adminAccess,
                         CurrentUser currentUser,
                         EntityManager em) {
        this.embeddingService = embeddingService;
        this.queryRewriter = queryRewriter;
        this.llmSummaryService = llmSummaryService;
        this.entityRepository = entityRepository;
        this.hubRepository = hubRepository;
        this.scopeResolver = scopeResolver;
        this.rerankService = rerankService;
        this.crossEncoderReranker = crossEncoderReranker;
        this.entityService = entityService;
        this.adminAccess = adminAccess;
        this.currentUser = currentUser;
        this.em = em;
    }

    /**
     * Run the discovery pipeline for a request and return ranked results with
     * their evidence. Candidates the cross-encoder finds irrelevant are dropped;
     * when none are left, {@link SearchResponse#sufficient()} is false. The
     * rationale of each result is generated separately, on request
     * ({@link #explain}), so the ranking is not held up by the LLM.
     * <p>
     * Not transactional: a transaction would hold a database connection through
     * the LLM, embedding and reranker calls — seconds each against hosted
     * models — and a handful of concurrent searches would drain the pool. Each
     * query borrows a connection for its own duration only.
     */
    public SearchResponse search(SearchRequest request) {
        String queryText = request.query();
        if (queryText == null || queryText.isBlank()) {
            return new SearchResponse("", false, List.of());
        }
        if (!hasSearchableText(queryText)) {
            // "x", "?": nothing to match on. The cross-encoder still scores such
            // a query against every card, and some land above the threshold.
            return new SearchResponse(queryText, false, List.of());
        }
        int matchCount = request.matchCount() != null ? request.matchCount() : 10;
        ResolvedScope scope = scopeResolver.resolve(request.scope());
        boolean tracing = traceAllowed(request);
        StageTimer timer = new StageTimer();
        // Someone is waiting: hosted models get this long in all, not their 2-minute default.
        long deadline = System.nanoTime() + inferenceBudget.toNanos();
        log.info("Search: \"{}\" (matchCount={}, scope={}, trace={})",
                queryText, matchCount, describeScope(request.scope()), tracing);

        // ── Stage 1: candidate retrieval ──

        // 1. The query as typed is embedded right away — the multilingual model
        //    needs no translation, so this overlaps with the LLM rewrite.
        CompletableFuture<String> originalVector = CompletableFuture.supplyAsync(
                () -> queryVector(queryText, deadline), ASYNC);

        //    One deterministic, cached rewrite prepares the other query forms:
        //    - the full English translation for the semantic leg. It keeps ALL
        //      terms — including project context like "SaaS" — so retrieval can
        //      surface candidates with relevant domain experience;
        //    - the condensed search terms, for the keyword leg and for Stage 2.
        //      Introduced for bge-reranker-v2-m3, which is trained on short
        //      search-style queries; the threshold of the current reranker is
        //      calibrated on these terms too.
        SearchQueryRewrite rewrite = queryRewriter.rewrite(queryText);
        String fullEnglishQuery = rewrite.english();
        String condensedQuery = rewrite.searchTerms();
        String lexicalQuery = lexicalQuery(condensedQuery);
        timer.mark("rewrite");

        // 2. Embed the English form. When it is the query as typed, reuse that
        //    vector and leave the original-language leg off (it would count the
        //    same ranking twice).
        boolean alreadyEnglish = sameText(queryText, fullEnglishQuery);
        boolean originalLanguageLeg = !alreadyEnglish && originalLanguageWeight > 0;
        String vectorStr;
        String originalVectorStr;
        if (alreadyEnglish) {
            vectorStr = join(originalVector);
            originalVectorStr = vectorStr;
            // The rewrite may differ from the typed text in case or spacing;
            // a rationale request looks the vector up by the rewrite.
            queryVectors.put(fullEnglishQuery, vectorStr);
        } else {
            vectorStr = queryVector(fullEnglishQuery, deadline);
            originalVectorStr = originalLanguageLeg ? join(originalVector) : vectorStr;
        }
        timer.mark("embed");
        int depth = Math.max(candidateDepth, matchCount);

        // Everything the trace needs that is known before retrieval. Candidates
        // are appended as they move through the stages.
        TraceContext trace = tracing
                ? new TraceContext(fullEnglishQuery, lexicalQuery, originalLanguageLeg, condensedQuery,
                        matchCount, depth, scope, vectorStr)
                : null;

        // 3. Scoped hybrid_search — HARD FILTER (§5) applied inside the SQL. Each
        //    leg ranks entities by their best chunk or profile document, then
        //    RRF fuses the legs; see V19__entity_candidate_retrieval.sql.
        Query searchQuery = em.createNativeQuery(HYBRID_SEARCH_SQL);
        searchQuery.setParameter(1, vectorStr);
        searchQuery.setParameter(2, originalVectorStr);
        searchQuery.setParameter(3, lexicalQuery);
        searchQuery.setParameter(4, toPgArray(scope.hubIds()));
        searchQuery.setParameter(5, toPgArray(scope.regionIds()));
        searchQuery.setParameter(6, depth);
        searchQuery.setParameter(7, CHUNK_POOL);
        searchQuery.setParameter(8, lexicalWeight);
        searchQuery.setParameter(9, semanticWeight);
        searchQuery.setParameter(10, originalLanguageLeg ? originalLanguageWeight : 0.0);
        searchQuery.setParameter(11, RRF_K);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = searchQuery.getResultList();
        timer.mark("retrieve");
        if (rows.isEmpty()) {
            log.info("No results for \"{}\"", queryText);
            return respond(queryText, false, List.of(), trace, List.of(), timer);
        }

        // 4. Load entities + evidence — one query each for the whole candidate
        //    list, not one per candidate. Evidence comes from sources only, never
        //    from the profile document: only a source can be cited.
        List<Long> candidateIds = rows.stream().map(row -> ((Number) row[0]).longValue()).toList();
        Map<Long, DiscoveryEntity> entitiesById = new HashMap<>();
        for (DiscoveryEntity entity : entityRepository.findAllById(candidateIds)) {
            entitiesById.put(entity.getId(), entity);
        }
        Map<Long, List<Evidence>> evidenceByEntity = fetchEvidence(candidateIds, vectorStr, EVIDENCE_K);

        List<Candidate> candidates = new ArrayList<>();
        int retrievalRank = 0;
        for (Object[] row : rows) {
            retrievalRank++;
            Long entityId = ((Number) row[0]).longValue();
            double base = ((Number) row[1]).doubleValue();
            DiscoveryEntity entity = entitiesById.get(entityId);
            if (entity == null) continue;
            List<Evidence> evidence = evidenceByEntity.getOrDefault(entityId, List.of());
            candidates.add(new Candidate(entity, base, evidence, retrievalRank,
                    nullableInt(row[2]), nullableInt(row[3]), nullableInt(row[4])));
        }
        timer.mark("evidence");
        if (candidates.isEmpty()) {
            return respond(queryText, false, List.of(), trace, candidates, timer);
        }

        // ── Stage 2: cross-encoder reranking (the relevance filter) ──

        // 5. The cross-encoder scores the best-retrieved candidates on their own
        //    passages — their profile card and their best evidence chunk — and
        //    drops those whose best passage is irrelevant. There is no
        //    bi-encoder similarity gate in front of it: e5 similarities sit in a
        //    narrow band that shifts with query length, so a fixed cut removed
        //    relevant candidates the cross-encoder never saw.
        //    A provider outage propagates as HTTP 503; never display un-reranked results.
        int reranked = Math.min(candidates.size(), Math.max(rerankDepth, matchCount));
        List<Candidate> toRerank = candidates.subList(0, reranked);
        List<Candidate> beyondDepth = candidates.subList(reranked, candidates.size());
        beyondDepth.forEach(c -> c.droppedAt = DROPPED_RERANK_DEPTH);

        Map<Long, List<String>> tagsByEntity = fetchCompetenceTags(
                (tracing ? candidates : toRerank).stream().map(c -> c.entity.getId()).toList());
        List<RerankCandidate> rerankInputs = rerankInputs(toRerank, tagsByEntity);
        List<RerankResult> scored = InferenceDeadline.within(deadline,
                () -> crossEncoderReranker.score(condensedQuery, rerankInputs));
        for (int i = 0; i < scored.size(); i++) toRerank.get(i).record(scored.get(i));
        List<RerankResult> relevant = crossEncoderReranker.keepRelevant(scored);
        timer.mark("rerank");

        //    A traced search also scores the candidates below the rerank depth, so
        //    the depth can be evaluated offline. Timed apart from "rerank" so the
        //    trace still shows what an untraced search costs; never shown.
        if (tracing && !beyondDepth.isEmpty()) {
            List<RerankResult> extra = crossEncoderReranker.score(condensedQuery, rerankInputs(beyondDepth, tagsByEntity));
            for (int i = 0; i < extra.size(); i++) beyondDepth.get(i).record(extra.get(i));
            timer.mark("trace-rerank");
        }

        // Map back to Candidate objects, preserving reranker order.
        List<Candidate> rerankedCandidates = new ArrayList<>(relevant.stream()
                .map(rr -> findCandidate(toRerank, rr.candidate().entity().getId()))
                .filter(c -> c != null)
                .toList());
        for (Candidate c : toRerank) {
            if (!rerankedCandidates.contains(c)) c.droppedAt = DROPPED_RERANKER;
        }

        if (rerankedCandidates.isEmpty()) {
            log.info("Search \"{}\" → {} candidates, all dropped by the cross-encoder", queryText, toRerank.size());
            return respond(queryText, false, List.of(), trace, candidates, timer);
        }

        // ── Soft re-rank: multiplicative boost on CE score (§6.3) ──

        // 6. Soft re-rank — multiply CE score by availability readiness factor.
        //    combined = ceScore × (1 + W_avail × overlap + W_open × open)
        //    This runs AFTER the cross-encoder so soft signals fine-tune the order
        //    of relevant candidates, not determine who passes the relevance filter.
        //    The boost scales with CE score: a weak match gets less absolute boost
        //    than a strong match, so availability matters more for relevant candidates.
        List<DiscoveryEntity> rerankedEntities = rerankedCandidates.stream()
                .map(c -> c.entity).toList();
        double[] boostFactors = rerankService.boostFactors(
                rerankedEntities, request.wantFrom(), request.wantTo(), request.wantPensumPct());

        for (int i = 0; i < rerankedCandidates.size(); i++) {
            Candidate c = rerankedCandidates.get(i);
            c.boostFactor = boostFactors[i];
            c.finalScore = c.rerankerScore * boostFactors[i];
        }

        // Re-sort by combined score (availability may reorder near-ties), then
        // keep the requested number of results — stage 1 retrieves far more
        // candidates than a page shows.
        rerankedCandidates.sort(Comparator.comparingDouble((Candidate c) -> c.finalScore).reversed());
        for (int i = matchCount; i < rerankedCandidates.size(); i++) {
            rerankedCandidates.get(i).droppedAt = DROPPED_RESULT_LIMIT;
        }
        if (rerankedCandidates.size() > matchCount) {
            rerankedCandidates = new ArrayList<>(rerankedCandidates.subList(0, matchCount));
        }
        for (int i = 0; i < rerankedCandidates.size(); i++) {
            rerankedCandidates.get(i).finalRank = i + 1;
        }

        log.info("Final ranking after soft re-rank: {}",
                rerankedCandidates.stream()
                        .map(c -> String.format("%s(%.3f)", c.entity.getDisplayName(), c.finalScore))
                        .toList());

        // 7. Results carry their evidence as chips. The grounded rationale is
        //    fetched per card (explain), after the list is on screen.
        Hub anchorHub = resolveAnchorHub(request.scope());
        List<SearchResult> results = new ArrayList<>();
        for (Candidate c : rerankedCandidates) {
            EntityDto dto = entityService.toDto(c.entity);
            results.add(new SearchResult(dto, c.finalScore, evidenceCitations(c.evidence),
                    distanceLabel(anchorHub, c.entity)));
        }
        timer.mark("assemble");

        log.info("Search \"{}\" → {} results (top: {})", queryText, results.size(), rerankedCandidates.get(0).entity.getDisplayName());
        return respond(queryText, true, results, trace, candidates, timer);
    }

    /**
     * The cross-encoder input of each candidate: its profile card, then its
     * best {@link #rerankPassages} evidence chunks, each with a short header.
     */
    private List<RerankCandidate> rerankInputs(List<Candidate> candidates, Map<Long, List<String>> tagsByEntity) {
        List<RerankCandidate> inputs = new ArrayList<>(candidates.size());
        for (Candidate c : candidates) {
            List<RerankPassage> passages = new ArrayList<>();
            String topEvidence = c.evidence.isEmpty() ? null : c.evidence.getFirst().content();
            passages.add(CrossEncoderReranker.cardPassage(
                    c.entity, tagsByEntity.getOrDefault(c.entity.getId(), List.of()), topEvidence,
                    rerankPassageChars));
            for (Evidence ev : c.evidence.subList(0, Math.min(rerankPassages, c.evidence.size()))) {
                passages.add(CrossEncoderReranker.evidencePassage(
                        c.entity, ev.sourceType(), ev.label(), ev.content(), rerankPassageChars));
            }
            inputs.add(new RerankCandidate(c.entity, c.base, passages));
        }
        return inputs;
    }

    // ─── Trace (evaluation diagnostics) ───

    /** A trace is returned only when asked for, and only to admins unless opened by config. */
    private boolean traceAllowed(SearchRequest request) {
        if (!Boolean.TRUE.equals(request.debug())) return false;
        return traceOpen || adminAccess.isAdmin(currentUser.userId().orElse(null));
    }

    /** Build the response, logging stage timings and attaching the trace when one was requested. */
    private SearchResponse respond(String queryText, boolean sufficient, List<SearchResult> results,
                                   TraceContext trace, List<Candidate> candidates, StageTimer timer) {
        Map<String, Long> timings = timer.finish();
        log.info("Search \"{}\" timings (ms): {}", queryText, timings);
        if (trace == null) {
            return new SearchResponse(queryText, sufficient, results);
        }
        List<CandidateTrace> candidateTraces = candidates.stream()
                .map(c -> new CandidateTrace(
                        c.entity.getId(), c.entity.getDisplayName(), c.retrievalRank, c.base,
                        c.lexicalRank, c.semanticRank, c.altSemanticRank,
                        c.bestSimilarity(), c.rerankerScore, c.rerankedOn, c.boostFactor,
                        c.boostFactor != null ? c.finalScore : null, c.finalRank, c.droppedAt))
                .toList();
        SearchTrace searchTrace = new SearchTrace(
                trace.retrievalQuery(), trace.lexicalQuery(), trace.originalLanguageLeg(),
                trace.rerankQuery(), trace.matchCount(), trace.candidateDepth(),
                rerankDepth, rerankPassages, crossEncoderReranker.relevanceThreshold(),
                timings, candidateTraces,
                semanticEntityRanking(trace.vectorStr(), trace.scope()));
        return new SearchResponse(queryText, sufficient, results, searchTrace);
    }

    /** Entities ranked by their best chunk — see {@link #SEMANTIC_ENTITY_RANKING_SQL}. */
    private List<SemanticEntityRank> semanticEntityRanking(String vectorStr, ResolvedScope scope) {
        String hubIds = toPgArray(scope.hubIds());
        String regionIds = toPgArray(scope.regionIds());
        Query query = em.createNativeQuery(SEMANTIC_ENTITY_RANKING_SQL);
        query.setParameter(1, vectorStr);
        query.setParameter(2, vectorStr);
        query.setParameter(3, hubIds);
        query.setParameter(4, regionIds);
        query.setParameter(5, regionIds);
        query.setParameter(6, hubIds);
        query.setParameter(7, SEMANTIC_ENTITY_RANKING_DEPTH);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        List<SemanticEntityRank> ranking = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            ranking.add(new SemanticEntityRank(
                    ((Number) row[0]).longValue(), (String) row[1],
                    ((Number) row[2]).intValue(), ((Number) row[3]).doubleValue()));
        }
        return ranking;
    }

    // ─── Grounding (§6.4 / §6.5) ───

    /**
     * The grounded German rationale for one search result, with the citations
     * that back it. Called per result card after the search returned, so the
     * ranking never waits for the LLM. Uses the same evidence the search showed:
     * the entity's best chunks for the English form of the query.
     *
     * @return the rationale, or {@code null} when the entity does not exist or is
     *         not active (→ 404)
     */
    public SearchRationale explain(SearchRationaleRequest request) {
        if (request == null || request.query() == null || request.query().isBlank()) {
            throw new IllegalArgumentException("query is required");
        }
        if (request.entityId() == null) {
            throw new IllegalArgumentException("entityId is required");
        }
        DiscoveryEntity entity = entityRepository.findById(request.entityId()).orElse(null);
        if (entity == null || !"active".equals(entity.getStatus())) return null;

        String queryText = request.query();
        String english = queryRewriter.rewrite(queryText).english();
        String vectorStr = queryVector(english, System.nanoTime() + inferenceBudget.toNanos());
        List<Evidence> evidence = fetchEvidence(List.of(entity.getId()), vectorStr, EVIDENCE_K)
                .getOrDefault(entity.getId(), List.of());
        Grounded grounded = ground(queryText, entity, evidence);
        return new SearchRationale(entity.getId(), grounded.rationale(), grounded.citations());
    }

    /**
     * Generate a grounded rationale and validate its citations. Keeps only claims
     * whose citations are all in the evidence set; a shown rationale always has
     * ≥1 validated citation. Degrades to evidence-only (rationale = null) when the
     * LLM is unavailable or nothing survives validation.
     * <p>
     * The LLM only <em>explains</em> the match. Relevance filtering is handled by
     * the cross-encoder reranker in Stage 2.
     */
    private Grounded ground(String queryText, DiscoveryEntity entity, List<Evidence> evidence) {
        if (evidence.isEmpty()) return new Grounded(null, List.of());

        // Assign stable citation ids S1, S2, … (one per source — already deduped).
        List<RationaleEvidence> forLlm = new ArrayList<>();
        for (int i = 0; i < evidence.size(); i++) {
            Evidence ev = evidence.get(i);
            forLlm.add(new RationaleEvidence("S" + (i + 1), ev.sourceType(), ev.label(), ev.content()));
        }

        MatchRationale out = llmSummaryService.generateMatchRationale(
                queryText, entity.getDisplayName(), entity.getHeadline(), entity.getOrgName(), forLlm);
        if (out == null) {
            return new Grounded(null, evidenceCitations(evidence));   // LLM off → evidence-only
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
            // No claims had valid citations — fall back to evidence-only chips.
            return new Grounded(null, evidenceCitations(evidence));
        }
        return new Grounded(out.rationale(), chips);
    }

    /** Every evidence source as a chip — what a card shows until (or without) a rationale. */
    private static List<Citation> evidenceCitations(List<Evidence> evidence) {
        return evidence.stream()
                .map(ev -> new Citation(ev.sourceId(), ev.label(), ev.sourceType(), ev.sourceUrl()))
                .toList();
    }

    /**
     * Competence tag labels of the given entities, alphabetically ordered.
     * Entities without tags are absent from the map.
     */
    private Map<Long, List<String>> fetchCompetenceTags(List<Long> entityIds) {
        if (entityIds.isEmpty()) return Map.of();
        Query tagsQuery = em.createNativeQuery(COMPETENCE_TAGS_SQL);
        tagsQuery.setParameter(1, toPgArray(entityIds));

        @SuppressWarnings("unchecked")
        List<Object[]> rows = tagsQuery.getResultList();
        Map<Long, List<String>> labels = new HashMap<>();
        for (Object[] row : rows) {
            labels.computeIfAbsent(((Number) row[0]).longValue(), id -> new ArrayList<>()).add((String) row[1]);
        }
        return labels;
    }

    // ─── Evidence ───

    /**
     * Fetch the top-k best-matching chunks of each entity, deduped by source (one
     * chunk per source = one candidate chip), ordered by semantic similarity
     * within the entity. Entities without active, embedded chunks are absent.
     */
    private Map<Long, List<Evidence>> fetchEvidence(List<Long> entityIds, String vectorStr, int k) {
        if (entityIds.isEmpty()) return Map.of();
        Query evidenceQuery = em.createNativeQuery(EVIDENCE_SQL);
        evidenceQuery.setParameter(1, vectorStr);
        evidenceQuery.setParameter(2, toPgArray(entityIds));
        evidenceQuery.setParameter(3, k);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = evidenceQuery.getResultList();

        Map<Long, List<Evidence>> evidence = new HashMap<>();
        for (Object[] row : rows) {
            Long entityId = ((Number) row[0]).longValue();
            Long sourceId = row[1] != null ? ((Number) row[1]).longValue() : null;
            String sourceType = (String) row[2];
            String label = (String) row[3];
            String sourceUrl = (String) row[4];
            String content = (String) row[5];
            double distance = ((Number) row[6]).doubleValue();
            evidence.computeIfAbsent(entityId, id -> new ArrayList<>())
                    .add(new Evidence(sourceId, sourceType, label, sourceUrl, content, -distance));
        }
        return evidence;
    }

    // ─── Helpers ───

    private Candidate findCandidate(List<Candidate> list, Long entityId) {
        for (Candidate c : list) {
            if (c.entity.getId().equals(entityId)) return c;
        }
        return null;
    }

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

    private static String describeScope(ScopeSelection scope) {
        if (scope == null || scope.kind() == null) return "all";
        return scope.kind() + (scope.hubId() != null ? ":hub" + scope.hubId() : "")
                + (scope.regionId() != null ? ":region" + scope.regionId() : "");
    }

    /**
     * The keyword-leg query: the condensed search terms without the role words
     * the condense prompt appends. Falls back to the terms as given if nothing
     * else is left.
     */
    static String lexicalQuery(String searchTerms) {
        if (searchTerms == null) return "";
        String topic = ROLE_WORDS.matcher(searchTerms).replaceAll(" ")
                .replaceAll("\\s+,", ",")
                .replaceAll(",(\\s*,)+", ",")
                .replaceAll("\\s+", " ")
                .replaceAll("^[\\s,]+|[\\s,]+$", "");
        return topic.isBlank() ? searchTerms.trim() : topic;
    }

    /**
     * Whether a query has at least two letters or digits — "AI" and "5G" do,
     * "x" and "?" do not.
     */
    static boolean hasSearchableText(String query) {
        return query != null && query.codePoints().filter(Character::isLetterOrDigit).limit(2).count() >= 2;
    }

    /** Whether two query texts are the same once whitespace and case are ignored. */
    static boolean sameText(String a, String b) {
        if (a == null || b == null) return a == b;
        return a.trim().replaceAll("\\s+", " ").equalsIgnoreCase(b.trim().replaceAll("\\s+", " "));
    }

    /** The future's value; a failure surfaces as the original exception (e.g. inference unavailable → 503). */
    private static <T> T join(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException cause) throw cause;
            throw e;
        }
    }

    private static Integer nullableInt(Object value) {
        return value == null ? null : ((Number) value).intValue();
    }

    private static String toPgArray(List<Long> ids) {
        return toPgArray(ids.stream().mapToLong(Long::longValue).toArray());
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

    private static final String DROPPED_RERANK_DEPTH = "rerank-depth";
    private static final String DROPPED_RERANKER = "reranker";
    private static final String DROPPED_RESULT_LIMIT = "result-limit";

    /**
     * A retrieved candidate with its base RRF score, evidence, and re-rank score,
     * plus where it left the pipeline ({@code droppedAt}, null while still in).
     */
    private static final class Candidate {
        final DiscoveryEntity entity;
        final double base;
        final List<Evidence> evidence;
        final int retrievalRank;
        final Integer lexicalRank;
        final Integer semanticRank;
        final Integer altSemanticRank;
        Double rerankerScore;
        String rerankedOn;
        Double boostFactor;
        double finalScore;
        Integer finalRank;
        String droppedAt;

        Candidate(DiscoveryEntity entity, double base, List<Evidence> evidence, int retrievalRank,
                  Integer lexicalRank, Integer semanticRank, Integer altSemanticRank) {
            this.entity = entity;
            this.base = base;
            this.evidence = evidence;
            this.retrievalRank = retrievalRank;
            this.lexicalRank = lexicalRank;
            this.semanticRank = semanticRank;
            this.altSemanticRank = altSemanticRank;
        }

        /** Take the cross-encoder's verdict: its score and the passage it came from. */
        void record(RerankResult result) {
            rerankerScore = result.rerankerScore();
            rerankedOn = result.bestPassage() != null ? result.bestPassage().label() : null;
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
    private record Grounded(String rationale, List<Citation> citations) {
    }

    /** Query-level inputs the trace reports, captured before retrieval. */
    private record TraceContext(String retrievalQuery, String lexicalQuery, boolean originalLanguageLeg,
                                String rerankQuery, int matchCount, int candidateDepth,
                                ResolvedScope scope, String vectorStr) {
    }

    /** Wall-clock time per pipeline stage, in the order the stages ran. */
    private static final class StageTimer {
        private final long start = System.nanoTime();
        private long last = start;
        private final Map<String, Long> timings = new LinkedHashMap<>();

        /** Record the time since the previous mark under {@code stage}. */
        void mark(String stage) {
            long now = System.nanoTime();
            timings.put(stage, (now - last) / 1_000_000);
            last = now;
        }

        /** The recorded stages plus {@code total}. */
        Map<String, Long> finish() {
            timings.put("total", (System.nanoTime() - start) / 1_000_000);
            return timings;
        }
    }
}
