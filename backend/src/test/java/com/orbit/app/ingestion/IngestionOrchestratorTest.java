package com.orbit.app.ingestion;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.orbit.app.embedding.EmbeddingKind;
import com.orbit.app.embedding.EmbeddingService;
import com.orbit.app.inference.InferenceUnavailableException;
import com.orbit.app.ingestion.dto.ExternalId;
import com.orbit.app.ingestion.dto.IngestionResult;
import com.orbit.app.ingestion.dto.IngestionStatus;
import com.orbit.app.ingestion.dto.RawContent;
import com.orbit.app.ingestion.dto.SourceRef;
import com.orbit.app.util.HashUtils;
import com.orbit.domain.entity.Source;
import com.orbit.domain.repository.ChunkRepository;
import com.orbit.domain.repository.DiscoveryEntityRepository;
import com.orbit.domain.repository.EntityIdentifierRepository;
import com.orbit.domain.repository.SourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The dedup and upsert contract of a single-source ingestion, without a database. */
class IngestionOrchestratorTest {

    private static final String URL = "https://github.com/ada/orbit";

    private SourceAdapter adapter;
    private SourceRepository sources;
    private ChunkRepository chunks;
    private EmbeddingService embeddings;
    private EntityIdentifierRepository identifiers;
    private IngestionOrchestrator orchestrator;

    private final SourceRef ref = new SourceRef(URL, "github", "harvested", "public_data", Map.of(), null);
    private final RawContent raw = new RawContent("readme text", Map.of(), Instant.parse("2026-11-15T10:00:00Z"));

    @BeforeEach
    void setUp() throws Exception {
        adapter = mock(SourceAdapter.class);
        sources = mock(SourceRepository.class);
        chunks = mock(ChunkRepository.class);
        embeddings = mock(EmbeddingService.class);
        identifiers = mock(EntityIdentifierRepository.class);
        SourceAdapterRegistry registry = mock(SourceAdapterRegistry.class);
        when(registry.getAdapter("github")).thenReturn(adapter);
        when(adapter.fetch(ref)).thenReturn(raw);
        when(adapter.contentFingerprint(raw)).thenReturn("orbit readme v2");
        when(sources.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        orchestrator = new IngestionOrchestrator(registry, mock(DiscoveryEntityRepository.class), identifiers,
                sources, chunks, embeddings, new ChunkingService(), mock(EntityEnrichmentService.class),
                TransactionOperations.withoutTransaction());
    }

    @Test
    void unchangedSourceIsSkippedBeforeAnyLlmOrEmbeddingCall() {
        Source stored = source("orbit readme v2");
        when(sources.findBySourceUrl(URL)).thenReturn(Optional.of(stored));

        IngestionResult result = orchestrator.ingest(ref);

        assertThat(result.status()).isEqualTo(IngestionStatus.SKIPPED);
        verify(adapter, never()).extractHighSignalCore(any());   // the LLM step
        verify(embeddings, never()).embedAll(anyList(), any());
        verify(sources, never()).save(any());
        verify(chunks, never()).deleteBySourceId(any());
    }

    @Test
    void changedSourceIsUpdatedInPlaceKeepingItsIdAndCurationFlag() {
        Source stored = source("orbit readme v1");
        stored.setActive(false);                                  // the user switched this repo off
        when(sources.findBySourceUrl(URL)).thenReturn(Optional.of(stored));
        when(adapter.extractHighSignalCore(raw)).thenReturn("Semantic search summary");
        when(adapter.buildLabel(raw)).thenReturn("github.com/ada/orbit");
        when(adapter.extractExternalIds(raw)).thenReturn(List.of(new ExternalId("github_login", "ada")));
        when(identifiers.findEntityIdByIdTypeAndIdValue("github_login", "ada")).thenReturn(Optional.of(7L));
        when(identifiers.existsByIdTypeAndIdValue("github_login", "ada")).thenReturn(true);
        when(embeddings.embedAll(List.of("Semantic search summary"), EmbeddingKind.PASSAGE))
                .thenReturn(List.of(new float[1024]));

        IngestionResult result = orchestrator.ingest(ref);

        assertThat(result.status()).isEqualTo(IngestionStatus.INGESTED);
        assertThat(result.sourceId()).isEqualTo(42L);
        verify(sources, never()).delete(any());
        verify(sources).save(stored);                             // the same row, not a replacement
        assertThat(stored.getContentHash()).isEqualTo(HashUtils.sha256Hex("orbit readme v2"));
        assertThat(stored.getLabel()).isEqualTo("github.com/ada/orbit");
        assertThat(stored.getRawContent()).isEqualTo("readme text");
        assertThat(stored.isActive()).isFalse();
        verify(chunks).deleteBySourceId(42L);
        // The chunk text goes to the embedding service as-is; the service adds the passage prefix.
        verify(embeddings).embedAll(List.of("Semantic search summary"), EmbeddingKind.PASSAGE);
    }

    @Test
    void aFailedEmbeddingWritesNothingAndPropagatesSoTheJobCanRetry() {
        when(sources.findBySourceUrl(URL)).thenReturn(Optional.empty());
        when(adapter.extractHighSignalCore(raw)).thenReturn("Semantic search summary");
        when(adapter.extractExternalIds(raw)).thenReturn(List.of());
        when(embeddings.embedAll(anyList(), any())).thenThrow(new InferenceUnavailableException("down"));

        assertThatThrownBy(() -> orchestrator.ingest(ref)).isInstanceOf(InferenceUnavailableException.class);

        verify(sources, never()).save(any());
        verify(chunks, never()).save(any());
    }

    @Test
    void adapterWithoutFingerprintFallsBackToTheRawText() {
        when(adapter.contentFingerprint(raw)).thenReturn(null);
        Source stored = source("readme text");
        when(sources.findBySourceUrl(URL)).thenReturn(Optional.of(stored));

        assertThat(orchestrator.ingest(ref).status()).isEqualTo(IngestionStatus.SKIPPED);
    }

    /** A stored source row (id 42) whose hash was computed over {@code fingerprint}. */
    private static Source source(String fingerprint) {
        Source source = new Source(7L, "github", URL, "old label", "harvested", "public_data",
                "old readme", HashUtils.sha256Hex(fingerprint), "ok", Instant.parse("2026-11-01T00:00:00Z"));
        try {
            var id = Source.class.getDeclaredField("id");
            id.setAccessible(true);
            id.set(source, 42L);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return source;
    }
}
