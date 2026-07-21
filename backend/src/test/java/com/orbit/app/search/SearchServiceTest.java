package com.orbit.app.search;

import java.util.List;

import com.orbit.app.embedding.EmbeddingKind;
import com.orbit.app.embedding.EmbeddingService;
import com.orbit.app.controllers.dto.EntityDto;
import com.orbit.app.llm.LlmSummaryService;
import com.orbit.app.profile.EntityService;
import com.orbit.app.search.dto.ResolvedScope;
import com.orbit.app.search.dto.ScopeSelection;
import com.orbit.app.search.dto.SearchRequest;
import com.orbit.domain.repository.DiscoveryEntityRepository;
import com.orbit.domain.repository.HubRepository;
import com.orbit.domain.entity.DiscoveryEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchServiceTest {

    private EmbeddingService embeddings;
    private LlmSummaryService llm;
    private ScopeResolver scopes;
    private EntityManager entityManager;
    private DiscoveryEntityRepository entities;
    private RerankService softReranker;
    private CrossEncoderReranker crossEncoder;
    private EntityService entityService;
    private SearchService service;

    @BeforeEach
    void setUp() {
        embeddings = mock(EmbeddingService.class);
        llm = mock(LlmSummaryService.class);
        scopes = mock(ScopeResolver.class);
        entityManager = mock(EntityManager.class);
        entities = mock(DiscoveryEntityRepository.class);
        softReranker = mock(RerankService.class);
        crossEncoder = mock(CrossEncoderReranker.class);
        entityService = mock(EntityService.class);
        service = new SearchService(embeddings, llm,
                entities, mock(HubRepository.class), scopes,
                softReranker, crossEncoder, entityService, entityManager);
    }

    @Test
    void blankQueryReturnsInsufficientWithoutCallingProviders() {
        var response = service.search(request("  "));

        assertThat(response.sufficient()).isFalse();
        assertThat(response.results()).isEmpty();
        verify(embeddings, never()).embed(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void emptyRetrievalResultStopsBeforeRerankingAndGrounding() {
        ScopeSelection selection = new ScopeSelection("all", null, null);
        when(scopes.resolve(selection)).thenReturn(new ResolvedScope(new long[0], new long[0]));
        when(llm.translateToEnglish("Java Expert")).thenReturn("Java expert");
        when(llm.translateAndCondenseSearchQuery("Java Expert")).thenReturn("Java expert");
        when(embeddings.embed("Java expert", EmbeddingKind.QUERY)).thenReturn(new float[]{0.1f, 0.2f});
        Query query = mock(Query.class);
        when(entityManager.createNativeQuery(org.mockito.ArgumentMatchers.anyString())).thenReturn(query);
        when(query.setParameter(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());

        var response = service.search(new SearchRequest("Java Expert", 5, selection,
                null, null, null));

        assertThat(response.query()).isEqualTo("Java Expert");
        assertThat(response.sufficient()).isFalse();
        assertThat(response.results()).isEmpty();
        verify(embeddings).embed("Java expert", EmbeddingKind.QUERY);
    }

    @Test
    void completePipelineReturnsGroundedEvidenceFallback() throws Exception {
        ScopeSelection selection = new ScopeSelection("all", null, null);
        when(scopes.resolve(selection)).thenReturn(new ResolvedScope(new long[0], new long[0]));
        when(llm.translateToEnglish("Java Expert")).thenReturn("Java expert");
        when(llm.translateAndCondenseSearchQuery("Java Expert")).thenReturn("java expert");
        when(embeddings.embed("Java expert", EmbeddingKind.QUERY)).thenReturn(new float[]{0.1f, 0.2f});

        Query retrieval = mock(Query.class);
        Query evidence = mock(Query.class);
        Query tags = mock(Query.class);
        when(entityManager.createNativeQuery(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(retrieval, evidence);
        when(entityManager.createNativeQuery(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(String.class))).thenReturn(tags);
        when(retrieval.setParameter(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(retrieval);
        when(evidence.setParameter(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(evidence);
        when(tags.setParameter(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(tags);
        when(retrieval.getResultList()).thenReturn(List.<Object[]>of(new Object[]{7L, 0.8d}));
        when(evidence.getResultList()).thenReturn(List.<Object[]>of(
                new Object[]{9L, "github", "github.com/ada/orbit", "https://github.com/ada/orbit",
                        "Java Spring semantic search", -0.92d}));
        when(tags.getResultList()).thenReturn(List.of("Java", "Spring Boot"));

        DiscoveryEntity entity = new DiscoveryEntity("person", "Ada", "Engineer", null);
        var id = DiscoveryEntity.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(entity, 7L);
        when(entities.findById(7L)).thenReturn(java.util.Optional.of(entity));
        when(crossEncoder.rerank(org.mockito.ArgumentMatchers.eq("java expert"),
                org.mockito.ArgumentMatchers.anyList())).thenAnswer(invocation -> {
            List<RerankCandidate> candidates = invocation.getArgument(1);
            return List.of(new RerankResult(candidates.getFirst(), 0.9d));
        });
        when(softReranker.boostFactors(org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull())).thenReturn(new double[]{1.1d});
        EntityDto dto = mock(EntityDto.class);
        when(entityService.toDto(entity)).thenReturn(dto);
        when(llm.generateMatchRationale(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(null);

        var response = service.search(new SearchRequest("Java Expert", 5, selection,
                null, null, null));

        assertThat(response.sufficient()).isTrue();
        assertThat(response.results()).singleElement().satisfies(result -> {
            assertThat(result.entity()).isSameAs(dto);
            assertThat(result.score()).isCloseTo(0.99d, org.assertj.core.data.Offset.offset(0.000001d));
            assertThat(result.rationale()).isNull();
            assertThat(result.citations()).singleElement().satisfies(citation ->
                    assertThat(citation.label()).isEqualTo("github.com/ada/orbit"));
        });
    }

    private static SearchRequest request(String query) {
        return new SearchRequest(query, null, null, null, null, null);
    }
}
