package com.orbit.app.ingestion;

import java.time.Instant;
import java.util.List;

import com.orbit.app.embedding.EmbeddingKind;
import com.orbit.app.embedding.EmbeddingService;
import com.orbit.app.inference.InferenceUnavailableException;
import com.orbit.domain.repository.ChunkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.support.TransactionOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReembeddingServiceTest {

    private static final Instant BEFORE = Instant.parse("2026-11-15T12:00:00Z");
    private static final String MODEL = "test-model";

    private ChunkRepository chunks;
    private EmbeddingService embeddings;

    @BeforeEach
    void setUp() {
        chunks = mock(ChunkRepository.class);
        embeddings = mock(EmbeddingService.class);
        when(embeddings.modelName()).thenReturn(MODEL);
    }

    @Test
    void reembedsStaleChunksFromTheirContentWithoutLoadingTheEntity() {
        when(chunks.findStaleEmbeddings(eq(BEFORE), eq(MODEL), eq(0L), any(Pageable.class))).thenReturn(List.of(
                new Object[]{11L, "robotics abstract"}, new Object[]{12L, "quantum abstract"}));
        when(chunks.findStaleEmbeddings(eq(BEFORE), eq(MODEL), eq(12L), any(Pageable.class))).thenReturn(List.of());
        when(embeddings.embedAll(List.of("robotics abstract", "quantum abstract"), EmbeddingKind.PASSAGE))
                .thenReturn(List.of(new float[]{0.1f, -2.5E-5f}, new float[]{0.2f, 1.0f}));
        when(chunks.updateEmbedding(any(), anyString(), anyString(), any())).thenReturn(1);

        int reembedded = service("").reembedStale(BEFORE);

        assertThat(reembedded).isEqualTo(2);
        verify(embeddings, never()).embed(anyString(), any());
        verify(chunks).updateEmbedding(eq(11L), eq("[0.1,-2.5E-5]"), eq(MODEL), any(Instant.class));
        verify(chunks).updateEmbedding(eq(12L), eq("[0.2,1.0]"), eq(MODEL), any(Instant.class));
        verify(chunks, never()).findById(any());
    }

    @Test
    void whenABatchFailsItsChunksAreEmbeddedOneByOneAndAFailingChunkIsSkipped() {
        when(chunks.findStaleEmbeddings(eq(BEFORE), eq(MODEL), eq(0L), any(Pageable.class))).thenReturn(List.of(
                new Object[]{11L, "robotics abstract"}, new Object[]{12L, "quantum abstract"}));
        when(chunks.findStaleEmbeddings(eq(BEFORE), eq(MODEL), eq(12L), any(Pageable.class))).thenReturn(List.of());
        when(embeddings.embedAll(anyList(), eq(EmbeddingKind.PASSAGE)))
                .thenThrow(new InferenceUnavailableException("down"));
        when(embeddings.embed("robotics abstract", EmbeddingKind.PASSAGE))
                .thenThrow(new InferenceUnavailableException("down"));
        when(embeddings.embed("quantum abstract", EmbeddingKind.PASSAGE)).thenReturn(new float[]{0.2f});
        when(chunks.updateEmbedding(eq(12L), anyString(), anyString(), any())).thenReturn(1);

        int reembedded = service("").reembedStale(BEFORE);

        assertThat(reembedded).isEqualTo(1);
        verify(chunks, never()).updateEmbedding(eq(11L), anyString(), anyString(), any());
    }

    @Test
    void aChunkDeletedMeanwhileIsNotCounted() {
        when(chunks.findStaleEmbeddings(eq(BEFORE), eq(MODEL), eq(0L), any(Pageable.class)))
                .thenReturn(List.<Object[]>of(new Object[]{11L, "robotics abstract"}));
        when(chunks.findStaleEmbeddings(eq(BEFORE), eq(MODEL), eq(11L), any(Pageable.class))).thenReturn(List.of());
        when(embeddings.embedAll(List.of("robotics abstract"), EmbeddingKind.PASSAGE))
                .thenReturn(List.<float[]>of(new float[]{0.1f}));
        when(chunks.updateEmbedding(any(), anyString(), anyString(), any())).thenReturn(0);

        assertThat(service("").reembedStale(BEFORE)).isZero();
    }

    @Test
    void withoutAUsableCutoffOnlyMissingAndOtherModelVectorsAreStale() {
        // The epoch: no chunk was embedded before it, so the cutoff selects nothing.
        assertThat(service("").cutoff()).isEqualTo(Instant.EPOCH);
        assertThat(service("yesterday").cutoff()).isEqualTo(Instant.EPOCH);
        assertThat(service(" 2026-11-15T12:00:00Z ").cutoff()).isEqualTo(BEFORE);
    }

    private ReembeddingService service(String reembedBefore) {
        return new ReembeddingService(chunks, embeddings, TransactionOperations.withoutTransaction(), reembedBefore);
    }
}
