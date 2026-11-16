package com.orbit.app.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.orbit.app.admin.AdminAccess;
import com.orbit.app.auth.CurrentUser;
import com.orbit.app.embedding.EmbeddingKind;
import com.orbit.app.embedding.EmbeddingService;
import com.orbit.app.controllers.dto.EntityDto;
import com.orbit.app.llm.LlmSummaryService;
import com.orbit.app.llm.dto.MatchRationale;
import com.orbit.app.llm.dto.RationaleClaim;
import com.orbit.app.llm.dto.SearchQueryRewrite;
import com.orbit.app.profile.EntityService;
import com.orbit.app.search.dto.CandidateTrace;
import com.orbit.app.search.dto.Citation;
import com.orbit.app.search.dto.ResolvedScope;
import com.orbit.app.search.dto.ScopeSelection;
import com.orbit.app.search.dto.SearchRequest;
import com.orbit.app.search.dto.SearchRationale;
import com.orbit.app.search.dto.SearchRationaleRequest;
import com.orbit.app.search.dto.SearchResponse;
import com.orbit.app.search.dto.SearchResult;
import com.orbit.domain.repository.DiscoveryEntityRepository;
import com.orbit.domain.repository.HubRepository;
import com.orbit.domain.entity.DiscoveryEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchServiceTest {

    private static final ScopeSelection ALL = new ScopeSelection("all", null, null);

    private EmbeddingService embeddings;
    private SearchQueryRewriter rewriter;
    private LlmSummaryService llm;
    private ScopeResolver scopes;
    private EntityManager entityManager;
    private DiscoveryEntityRepository entities;
    private RerankService softReranker;
    private CrossEncoderReranker crossEncoder;
    private EntityService entityService;
    private AdminAccess adminAccess;
    private CurrentUser currentUser;
    private SearchService service;
    private final List<DiscoveryEntity> storedEntities = new ArrayList<>();

    @BeforeEach
    void setUp() {
        embeddings = mock(EmbeddingService.class);
        rewriter = mock(SearchQueryRewriter.class);
        llm = mock(LlmSummaryService.class);
        scopes = mock(ScopeResolver.class);
        entityManager = mock(EntityManager.class);
        entities = mock(DiscoveryEntityRepository.class);
        softReranker = mock(RerankService.class);
        crossEncoder = mock(CrossEncoderReranker.class);
        entityService = mock(EntityService.class);
        adminAccess = mock(AdminAccess.class);
        currentUser = mock(CurrentUser.class);
        when(entities.findAllById(any())).thenAnswer(invocation -> List.copyOf(storedEntities));
        service = serviceWith(crossEncoder);
    }

    @Test
    void blankQueryReturnsInsufficientWithoutCallingProviders() {
        var response = service.search(request("  "));

        assertThat(response.sufficient()).isFalse();
        assertThat(response.results()).isEmpty();
        verify(rewriter, never()).rewrite(anyString());
        verify(embeddings, never()).embed(anyString(), any());
    }

    @Test
    void emptyRetrievalResultStopsBeforeRerankingAndGrounding() {
        stubQueryPreparation();
        Query query = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyInt(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());

        var response = service.search(new SearchRequest("Java Expert", 5, ALL,
                null, null, null, null));

        assertThat(response.query()).isEqualTo("Java Expert");
        assertThat(response.sufficient()).isFalse();
        assertThat(response.results()).isEmpty();
        assertThat(response.trace()).isNull();
        // Already English: the query as typed is embedded once and the
        // original-language leg stays off instead of counting the same ranking twice.
        verify(embeddings, times(1)).embed(anyString(), eq(EmbeddingKind.QUERY));
        verify(embeddings).embed("Java Expert", EmbeddingKind.QUERY);
        verify(query).setParameter(3, "java");                     // keywords without the role word
        verify(query).setParameter(6, 50);                         // candidate depth, not matchCount
        verify(query).setParameter(10, 0.0d);                      // original-language leg off
        verify(crossEncoder, never()).score(anyString(), anyList());
    }

    @Test
    void aQueryInAnotherLanguageIsAlsoRetrievedAsTyped() {
        when(scopes.resolve(ALL)).thenReturn(new ResolvedScope(new long[0], new long[0]));
        when(rewriter.rewrite("Wir suchen Java-Expertise")).thenReturn(
                new SearchQueryRewrite("We are looking for Java expertise", "Java, expert"));
        when(embeddings.embed(anyString(), eq(EmbeddingKind.QUERY))).thenReturn(new float[]{0.1f, 0.2f});
        Query query = nativeQuery(List.of());
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);

        service.search(new SearchRequest("Wir suchen Java-Expertise", 5, ALL, null, null, null, null));

        verify(embeddings).embed("Wir suchen Java-Expertise", EmbeddingKind.QUERY);
        verify(embeddings).embed("We are looking for Java expertise", EmbeddingKind.QUERY);
        verify(query).setParameter(3, "Java");
        verify(query).setParameter(10, 1.0d);                      // original-language leg on
    }

    @Test
    void resultsAreCappedAtTheRequestedCountAndTheRestTracedAsResultLimit() throws Exception {
        service = serviceWith(realReranker(new ArrayList<>()));
        ReflectionTestUtils.setField(service, "traceOpen", true);
        stubQueryPreparation();
        stubRetrieval(List.<Object[]>of(retrievalRow(7L, 0.03d), retrievalRow(9L, 0.02d)),
                List.<Object[]>of(evidenceRow(7L, 0.92d), evidenceRow(9L, 0.92d)), List.of());
        DiscoveryEntity ada = entity(7L, "Ada");
        entity(9L, "Cy");
        when(softReranker.boostFactors(anyList(), isNull(), isNull(), isNull())).thenReturn(new double[]{1.0d, 1.0d});
        when(entityService.toDto(ada)).thenReturn(mock(EntityDto.class));

        SearchResponse response = service.search(new SearchRequest("Java Expert", 1, ALL,
                null, null, null, true));

        assertThat(response.results()).hasSize(1);
        assertThat(response.trace().matchCount()).isEqualTo(1);
        assertThat(response.trace().candidateDepth()).isEqualTo(50);
        assertThat(response.trace().candidates()).extracting(CandidateTrace::droppedAt)
                .containsExactly(null, "result-limit");
        assertThat(response.trace().candidates()).extracting(CandidateTrace::finalRank)
                .containsExactly(1, null);
        assertThat(response.trace().candidates().get(1).finalScore()).isEqualTo(0.7d);
    }

    @Test
    void theKeywordQueryDropsRoleWordsButNeverEndsUpEmpty() {
        assertThat(SearchService.lexicalQuery("secure networked robotics, Switzerland, expert"))
                .isEqualTo("secure networked robotics, Switzerland");
        assertThat(SearchService.lexicalQuery("Microorganism expert, wastewater treatment"))
                .isEqualTo("Microorganism, wastewater treatment");
        assertThat(SearchService.lexicalQuery("Expertise, critical metal recovery, applied circular economy expert"))
                .isEqualTo("critical metal recovery, applied circular economy");
        assertThat(SearchService.lexicalQuery("Social media marketing, brand strategy, consultant"))
                .isEqualTo("Social media marketing, brand strategy");
        assertThat(SearchService.lexicalQuery("Expert")).isEqualTo("Expert");
        assertThat(SearchService.sameText(" Java  Expert", "java expert")).isTrue();
        assertThat(SearchService.sameText("Java Experte", "Java expert")).isFalse();
    }

    @Test
    void completePipelineReturnsRankedResultsWithTheirEvidenceAndNoRationale() throws Exception {
        stubQueryPreparation();
        stubRetrieval(List.<Object[]>of(retrievalRow(7L, 0.8d)),
                List.<Object[]>of(evidenceRow(7L, 0.92d)), null);
        DiscoveryEntity entity = entity(7L, "Ada");
        when(crossEncoder.score(eq("java expert"), anyList())).thenAnswer(invocation -> {
            List<RerankCandidate> candidates = invocation.getArgument(1);
            return List.of(new RerankResult(candidates.getFirst(), 0.9d, candidates.getFirst().passages().getFirst()));
        });
        when(crossEncoder.keepRelevant(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(softReranker.boostFactors(anyList(), isNull(), isNull(), isNull())).thenReturn(new double[]{1.1d});
        EntityDto dto = mock(EntityDto.class);
        when(entityService.toDto(entity)).thenReturn(dto);

        var response = service.search(new SearchRequest("Java Expert", 5, ALL,
                null, null, null, null));

        assertThat(response.sufficient()).isTrue();
        assertThat(response.trace()).isNull();
        assertThat(response.results()).singleElement().satisfies(result -> {
            assertThat(result.entity()).isSameAs(dto);
            assertThat(result.score()).isCloseTo(0.99d, org.assertj.core.data.Offset.offset(0.000001d));
            assertThat(result.citations()).singleElement().satisfies(citation ->
                    assertThat(citation.label()).isEqualTo("github.com/ada/orbit"));
        });
        // The rationale is generated per card, on request — never inside the search.
        verify(llm, never()).generateMatchRationale(any(), any(), any(), any(), any());
    }

    @Test
    void byDefaultTheCrossEncoderScoresOnlyTheProfileCard() throws Exception {
        List<List<String>> scored = new ArrayList<>();
        service = serviceWith(realReranker(scored));
        stubThreeCandidates(null);

        service.search(new SearchRequest("Java Expert", 5, ALL, null, null, null, null));

        assertThat(scored).singleElement().satisfies(documents -> assertThat(documents).containsExactly(
                "Ada: Engineer. Expertise: Java, Spring Boot Matching evidence: Java Spring semantic search",
                "Bob: Engineer. Expertise: Go Matching evidence: Java Spring semantic search",
                "Cy: Engineer Matching evidence: Java Spring semantic search"));
    }

    @Test
    void passagesAreCutToTheConfiguredLength() throws Exception {
        List<List<String>> scored = new ArrayList<>();
        service = serviceWith(realReranker(scored));
        ReflectionTestUtils.setField(service, "rerankPassageChars", 12);
        ReflectionTestUtils.setField(service, "rerankPassages", 1);
        stubThreeCandidates(null);

        service.search(new SearchRequest("Java Expert", 5, ALL, null, null, null, null));

        assertThat(scored).singleElement().satisfies(documents ->
                assertThat(documents).hasSize(6).allSatisfy(text -> assertThat(text).hasSizeLessThanOrEqualTo(12)));
    }

    @Test
    void aQueryWithoutTwoLettersOrDigitsReturnsNothingAndCallsNoModel() {
        for (String query : List.of("x", "?", " - ", "é")) {
            SearchResponse response = service.search(new SearchRequest(query, 5, ALL, null, null, null, null));
            assertThat(response.sufficient()).isFalse();
            assertThat(response.results()).isEmpty();
        }
        verify(embeddings, never()).embed(any(), any());
        assertThat(SearchService.hasSearchableText("AI")).isTrue();
        assertThat(SearchService.hasSearchableText("5G")).isTrue();
        assertThat(SearchService.hasSearchableText("Ökobilanz")).isTrue();
    }

    @Test
    void withAnEvidencePassageTheCrossEncoderScoresTheCardAndTheBestChunk() throws Exception {
        List<List<String>> scored = new ArrayList<>();
        service = serviceWith(realReranker(scored));
        ReflectionTestUtils.setField(service, "rerankPassages", 1);
        stubThreeCandidates(null);

        service.search(new SearchRequest("Java Expert", 5, ALL, null, null, null, null));

        assertThat(scored).singleElement().satisfies(documents -> assertThat(documents).containsExactly(
                "Ada: Engineer. Expertise: Java, Spring Boot Matching evidence: Java Spring semantic search",
                "Ada — Engineer\nGitHub repository: github.com/ada/orbit\n\nJava Spring semantic search",
                "Bob: Engineer. Expertise: Go Matching evidence: Java Spring semantic search",
                "Bob — Engineer\nGitHub repository: github.com/ada/orbit\n\nJava Spring semantic search",
                "Cy: Engineer Matching evidence: Java Spring semantic search",
                "Cy — Engineer\nGitHub repository: github.com/ada/orbit\n\nJava Spring semantic search"));
    }

    @Test
    void aLowBiEncoderSimilarityNoLongerKeepsARelevantCandidateFromTheCrossEncoder() throws Exception {
        service = serviceWith(realReranker(new ArrayList<>()));
        stubThreeCandidates(null);
        when(entityService.toDto(storedEntities.get(2))).thenReturn(mock(EntityDto.class));

        SearchResponse response = service.search(new SearchRequest("Java Expert", 5, ALL, null, null, null, null));

        // Cy's best chunk is only 0.50 similar, but the cross-encoder finds it relevant.
        assertThat(response.results()).hasSize(2);
        assertThat(response.results()).extracting(SearchResult::score).containsExactly(0.9d, 0.7d);
    }

    @Test
    void candidatesAreLoadedWithOneQueryPerKindNotOnePerCandidate() throws Exception {
        service = serviceWith(realReranker(new ArrayList<>()));
        stubThreeCandidates(null);

        service.search(new SearchRequest("Java Expert", 5, ALL, null, null, null, null));

        verify(entities, times(1)).findAllById(any());
        verify(entities, never()).findById(anyLong());
        // retrieval + evidence + tags, however many candidates came back
        verify(entityManager, times(3)).createNativeQuery(anyString());
    }

    @Test
    void adminTraceRecordsEveryCandidateWithTheStageThatDroppedIt() throws Exception {
        List<List<String>> scored = new ArrayList<>();
        service = serviceWith(realReranker(scored));
        ReflectionTestUtils.setField(service, "rerankDepth", 2);
        ReflectionTestUtils.setField(service, "rerankPassages", 1);
        when(currentUser.userId()).thenReturn(Optional.of("admin-user"));
        when(adminAccess.isAdmin("admin-user")).thenReturn(true);
        stubThreeCandidates(List.<Object[]>of(
                new Object[]{9L, "Cy", 1, 0.95d},
                new Object[]{7L, "Ada", 2, 0.92d}));

        SearchResponse response = service.search(new SearchRequest("Java Expert", 1, ALL,
                null, null, null, true));

        assertThat(response.results()).hasSize(1);
        // The two candidates within the rerank depth, then — for the trace only —
        // the one below it, timed apart so "rerank" stays what a real search costs.
        assertThat(scored).extracting(List::size).containsExactly(4, 2);
        assertThat(response.trace()).isNotNull();
        assertThat(response.trace().rerankDepth()).isEqualTo(2);
        assertThat(response.trace().rerankPassages()).isEqualTo(1);
        assertThat(response.trace().rerankerThreshold()).isEqualTo(0.04d);
        assertThat(response.trace().retrievalQuery()).isEqualTo("Java expert");
        assertThat(response.trace().lexicalQuery()).isEqualTo("java");
        assertThat(response.trace().originalLanguageLeg()).isFalse();
        assertThat(response.trace().rerankQuery()).isEqualTo("java expert");
        assertThat(response.trace().timingsMs()).containsKeys(
                "rewrite", "embed", "retrieve", "evidence", "rerank", "trace-rerank", "assemble", "total");

        List<CandidateTrace> candidates = response.trace().candidates();
        assertThat(candidates).extracting(CandidateTrace::displayName).containsExactly("Ada", "Bob", "Cy");
        assertThat(candidates).extracting(CandidateTrace::retrievalRank).containsExactly(1, 2, 3);
        assertThat(candidates).extracting(CandidateTrace::lexicalRank).containsExactly(1, 1, 1);
        assertThat(candidates).extracting(CandidateTrace::altSemanticRank).containsOnlyNulls();
        assertThat(candidates).extracting(CandidateTrace::droppedAt)
                .containsExactly(null, "reranker", "rerank-depth");
        assertThat(candidates).extracting(CandidateTrace::rerankerScore).containsExactly(0.9d, 0.01d, 0.7d);
        // The card comes first and wins the tie with the chunk.
        assertThat(candidates).extracting(CandidateTrace::rerankedOn).containsOnly(RerankPassage.CARD_LABEL);
        assertThat(candidates).extracting(CandidateTrace::finalRank).containsExactly(1, null, null);
        assertThat(candidates).extracting(CandidateTrace::finalScore).containsExactly(0.9d, null, null);
        assertThat(candidates.get(2).bestSimilarity()).isEqualTo(0.50d);

        assertThat(response.trace().semanticEntityRanking()).first().satisfies(rank -> {
            assertThat(rank.displayName()).isEqualTo("Cy");
            assertThat(rank.bestChunkRank()).isEqualTo(1);
        });
    }

    @Test
    void traceIsWithheldFromNonAdminsAndCandidatesBelowTheRerankDepthAreNotScored() throws Exception {
        List<List<String>> scored = new ArrayList<>();
        service = serviceWith(realReranker(scored));
        ReflectionTestUtils.setField(service, "rerankDepth", 2);
        ReflectionTestUtils.setField(service, "rerankPassages", 1);
        when(currentUser.userId()).thenReturn(Optional.of("someone"));
        when(adminAccess.isAdmin("someone")).thenReturn(false);
        stubThreeCandidates(null);

        SearchResponse response = service.search(new SearchRequest("Java Expert", 1, ALL,
                null, null, null, true));

        assertThat(response.trace()).isNull();
        assertThat(response.results()).hasSize(1);
        assertThat(scored).extracting(List::size).containsExactly(4);   // Ada and Bob, two passages each
    }

    @Test
    void traceOpenConfigAllowsTheTraceWithoutAnAdmin() throws Exception {
        service = serviceWith(realReranker(new ArrayList<>()));
        ReflectionTestUtils.setField(service, "traceOpen", true);
        stubThreeCandidates(List.of());

        SearchResponse response = service.search(new SearchRequest("Java Expert", 5, ALL,
                null, null, null, true));

        assertThat(response.trace()).isNotNull();
        assertThat(response.trace().candidates()).hasSize(3);
    }

    // ─── explain (lazy rationale) ───

    @Test
    void explainGroundsTheRationaleOnTheSearchEvidenceWithoutEmbeddingAgain() throws Exception {
        service = serviceWith(realReranker(new ArrayList<>()));
        stubThreeCandidates(null);
        service.search(new SearchRequest("Java Expert", 5, ALL, null, null, null, null));
        DiscoveryEntity ada = storedEntities.getFirst();
        when(entities.findById(7L)).thenReturn(Optional.of(ada));
        Query evidence = nativeQuery(List.<Object[]>of(evidenceRow(7L, 0.92d)));
        when(entityManager.createNativeQuery(anyString())).thenReturn(evidence);
        when(llm.generateMatchRationale(eq("Java Expert"), eq("Ada"), eq("Engineer"), isNull(), anyList()))
                .thenReturn(new MatchRationale("Ada baut Java-Suchsysteme.", List.of(
                        new RationaleClaim("Java", List.of("S1")),
                        new RationaleClaim("erfunden", List.of("S9")))));

        SearchRationale rationale = service.explain(new SearchRationaleRequest("Java Expert", 7L));

        assertThat(rationale.entityId()).isEqualTo(7L);
        assertThat(rationale.rationale()).isEqualTo("Ada baut Java-Suchsysteme.");
        assertThat(rationale.citations()).extracting(Citation::label).containsExactly("github.com/ada/orbit");
        verify(embeddings, times(1)).embed(anyString(), eq(EmbeddingKind.QUERY));   // the search's only
        verify(evidence).setParameter(eq(1), anyString());
    }

    @Test
    void explainFallsBackToEveryEvidenceSourceWithoutTheLlm() throws Exception {
        stubQueryPreparation();
        DiscoveryEntity ada = entity(7L, "Ada");
        when(entities.findById(7L)).thenReturn(Optional.of(ada));
        Query evidence = nativeQuery(List.<Object[]>of(evidenceRow(7L, 0.92d)));
        when(entityManager.createNativeQuery(anyString())).thenReturn(evidence);

        SearchRationale rationale = service.explain(new SearchRationaleRequest("Java Expert", 7L));

        assertThat(rationale.rationale()).isNull();
        assertThat(rationale.citations()).extracting(Citation::label).containsExactly("github.com/ada/orbit");
        verify(embeddings).embed("Java expert", EmbeddingKind.QUERY);   // not searched before: embedded once
    }

    @Test
    void explainRejectsBadRequestsAndReportsUnknownOrInactiveEntitiesAsMissing() throws Exception {
        assertThatThrownBy(() -> service.explain(new SearchRationaleRequest(" ", 7L)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.explain(new SearchRationaleRequest("Java", null)))
                .isInstanceOf(IllegalArgumentException.class);

        when(entities.findById(7L)).thenReturn(Optional.empty());
        assertThat(service.explain(new SearchRationaleRequest("Java", 7L))).isNull();

        DiscoveryEntity hidden = entity(8L, "Hidden");
        hidden.setStatus("hidden");
        when(entities.findById(8L)).thenReturn(Optional.of(hidden));
        assertThat(service.explain(new SearchRationaleRequest("Java", 8L))).isNull();
        verify(llm, never()).generateMatchRationale(any(), any(), any(), any(), any());
    }

    // ─── fixtures ───

    private SearchService serviceWith(CrossEncoderReranker reranker) {
        return new SearchService(embeddings, rewriter, llm,
                entities, mock(HubRepository.class), scopes,
                softReranker, reranker, entityService, adminAccess, currentUser, entityManager);
    }

    /**
     * A real reranker (threshold 0.04) over a fake provider that scores by the
     * name each passage starts with; records the documents of every call.
     */
    private static CrossEncoderReranker realReranker(List<List<String>> scored) {
        return new CrossEncoderReranker((query, documents) -> {
            scored.add(List.copyOf(documents));
            return documents.stream()
                    .map(document -> document.startsWith("Ada") ? 0.9d
                            : document.startsWith("Bob") ? 0.01d : 0.7d)
                    .toList();
        }, 0.04d);
    }

    /**
     * Ada passes the reranker, Bob fails it, Cy passes it although its evidence
     * is only 0.50 similar. {@code semanticRanking} is the trace-only ranking
     * query's rows (null when no trace is expected).
     */
    private void stubThreeCandidates(List<Object[]> semanticRanking) throws Exception {
        stubQueryPreparation();
        stubRetrieval(List.<Object[]>of(retrievalRow(7L, 0.03d), retrievalRow(8L, 0.02d), retrievalRow(9L, 0.01d)),
                List.<Object[]>of(evidenceRow(7L, 0.92d), evidenceRow(8L, 0.92d), evidenceRow(9L, 0.50d)),
                semanticRanking);
        DiscoveryEntity ada = entity(7L, "Ada");
        entity(8L, "Bob");
        entity(9L, "Cy");
        when(softReranker.boostFactors(anyList(), isNull(), isNull(), isNull())).thenAnswer(invocation -> {
            double[] neutral = new double[invocation.<List<?>>getArgument(0).size()];
            java.util.Arrays.fill(neutral, 1.0d);
            return neutral;
        });
        when(entityService.toDto(ada)).thenReturn(mock(EntityDto.class));
    }

    private void stubQueryPreparation() {
        when(scopes.resolve(ALL)).thenReturn(new ResolvedScope(new long[0], new long[0]));
        when(rewriter.rewrite("Java Expert")).thenReturn(new SearchQueryRewrite("Java expert", "java expert"));
        when(embeddings.embed(anyString(), eq(EmbeddingKind.QUERY))).thenReturn(new float[]{0.1f, 0.2f});
    }

    /** One hybrid_search row: (entity_id, score, lexical_rank, semantic_rank, alt_semantic_rank). */
    private static Object[] retrievalRow(long entityId, double score) {
        return new Object[]{entityId, score, 1, 1, null};
    }

    /**
     * Stub the native queries in the order the service issues them: hybrid
     * retrieval, evidence for all candidates, tags for the candidates being
     * scored, then (traced searches only) the entity-collapsed semantic ranking.
     */
    private void stubRetrieval(List<Object[]> retrievalRows, List<Object[]> evidenceRows,
                               List<Object[]> semanticRanking) {
        List<Query> queries = new ArrayList<>();
        queries.add(nativeQuery(retrievalRows));
        queries.add(nativeQuery(evidenceRows));
        queries.add(nativeQuery(List.<Object[]>of(
                new Object[]{7L, "Java"}, new Object[]{7L, "Spring Boot"}, new Object[]{8L, "Go"})));
        if (semanticRanking != null) {
            queries.add(nativeQuery(semanticRanking));
        }
        when(entityManager.createNativeQuery(anyString()))
                .thenReturn(queries.getFirst(), queries.subList(1, queries.size()).toArray(Query[]::new));
    }

    /** One evidence row: (entity_id, source_id, source_type, label, source_url, content, distance). */
    private static Object[] evidenceRow(long entityId, double similarity) {
        return new Object[]{entityId, 9L, "github", "github.com/ada/orbit", "https://github.com/ada/orbit",
                "Java Spring semantic search", -similarity};
    }

    private static Query nativeQuery(List<Object[]> rows) {
        Query query = mock(Query.class);
        when(query.setParameter(anyInt(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(rows);
        return query;
    }

    private DiscoveryEntity entity(long id, String name) throws Exception {
        DiscoveryEntity entity = new DiscoveryEntity("person", name, "Engineer", null);
        var idField = DiscoveryEntity.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(entity, id);
        storedEntities.add(entity);
        return entity;
    }

    private static SearchRequest request(String query) {
        return new SearchRequest(query, null, null, null, null, null, null);
    }
}
