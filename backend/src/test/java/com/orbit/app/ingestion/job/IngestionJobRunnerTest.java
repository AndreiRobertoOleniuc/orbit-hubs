package com.orbit.app.ingestion.job;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.orbit.app.inference.InferenceUnavailableException;
import com.orbit.app.ingestion.EntityEnrichmentService;
import com.orbit.app.ingestion.IngestionOrchestrator;
import com.orbit.app.ingestion.dto.IngestionResult;
import com.orbit.app.ingestion.dto.SourceRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IngestionJobRunnerTest {

    private final JsonMapper json = JsonMapper.builder().build();
    private IngestionJobStore store;
    private IngestionOrchestrator orchestrator;
    private EntityEnrichmentService enrichment;
    private IngestionJobRunner runner;

    @BeforeEach
    void setUp() {
        store = mock(IngestionJobStore.class);
        orchestrator = mock(IngestionOrchestrator.class);
        enrichment = mock(EntityEnrichmentService.class);
        runner = new IngestionJobRunner(store, orchestrator, enrichment, TransactionOperations.withoutTransaction(),
                json, Duration.ofSeconds(30), Duration.ofSeconds(3));
    }

    @Test
    void aSourceJobIngestsWithoutEnrichingThenArmsOneEnrichment() {
        when(orchestrator.ingestMulti(any(), eq(false))).thenReturn(List.of(IngestionResult.ingested(7L, 8L, 3)));

        runner.run(sourceJob(null, 1));

        verify(store).succeed(eq(1L), eq(7L), contains("\"totalChunks\":3"));
        verify(store).armEnrichment("ada", 7L, Duration.ofSeconds(3));
        verify(enrichment, never()).enrich(any());
    }

    @Test
    void anUploadIsHandedToTheAdapterAsATemporaryFileAndDeletedAfterwards() {
        AtomicReference<Path> seen = new AtomicReference<>();
        when(orchestrator.ingestMulti(any(), eq(false))).thenAnswer(invocation -> {
            SourceRef ref = invocation.getArgument(0);
            Path file = Path.of(ref.context().get("file_path"));
            assertThat(Files.readAllBytes(file)).containsExactly(1, 2, 3);
            assertThat(ref.context()).containsEntry("filename", "cv.pdf");
            seen.set(file);
            return List.of(IngestionResult.ingested(7L, 8L, 1));
        });

        runner.run(sourceJob(new byte[]{1, 2, 3}, 1));

        assertThat(seen.get()).isNotNull().doesNotExist();
    }

    @Test
    void anUnavailableProviderIsRetriedWithGrowingBackoffUntilAttemptsRunOut() {
        when(orchestrator.ingestMulti(any(), eq(false))).thenThrow(new InferenceUnavailableException("down"));

        runner.run(sourceJob(null, 1));
        verify(store).retryLater(eq(1L), contains("down"), eq(Duration.ofSeconds(30)));

        runner.run(sourceJob(null, 2));
        verify(store).retryLater(eq(1L), contains("down"), eq(Duration.ofMinutes(2)));

        runner.run(sourceJob(null, 3));
        verify(store).fail(eq(1L), contains("down"));
    }

    @Test
    void invalidInputFailsAtOnce() {
        when(orchestrator.ingestMulti(any(), eq(false))).thenThrow(new IllegalArgumentException("bad login"));

        runner.run(sourceJob(null, 1));

        verify(store).fail(eq(1L), contains("bad login"));
        verify(store, never()).retryLater(any(Long.class), anyString(), any());
    }

    @Test
    void enrichAndResyncJobsRunOnTheirEntity() {
        runner.run(new IngestionJob(2L, "ada", IngestionJob.ENRICH, null, null, null, 7L,
                IngestionJob.RUNNING, 1, 3, null, null, null, null));
        verify(enrichment).enrich(7L);
        verify(store).succeed(2L, 7L, null);

        when(orchestrator.resyncEntity(7L)).thenReturn(List.of(IngestionResult.skipped(7L)));
        runner.run(new IngestionJob(3L, "ada", IngestionJob.RESYNC, null, null, null, 7L,
                IngestionJob.RUNNING, 1, 3, null, null, null, null));
        verify(store).succeed(eq(3L), eq(7L), contains("\"skipped\":1"));
    }

    private IngestionJob sourceJob(byte[] payload, int attempt) {
        SourceRef ref = new SourceRef("cv:pdf:abc", "cv_pdf", "user_supplied", "user_consent",
                Map.of("filename", "cv.pdf"), "ada", true, "supply");
        return new IngestionJob(1L, "ada", IngestionJob.SOURCE, "cv_pdf", json.writeValueAsString(ref), payload,
                null, IngestionJob.RUNNING, attempt, 3, null, null, null, null);
    }
}
