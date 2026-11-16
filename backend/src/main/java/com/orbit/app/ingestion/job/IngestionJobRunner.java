package com.orbit.app.ingestion.job;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import com.orbit.app.ingestion.EntityEnrichmentService;
import com.orbit.app.ingestion.IngestionOrchestrator;
import com.orbit.app.ingestion.dto.IngestionResult;
import com.orbit.app.ingestion.dto.IngestionSummary;
import com.orbit.app.ingestion.dto.SourceRef;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Runs one claimed job to its next state: succeeded, failed, or queued again
 * after a transient failure (exponential backoff, up to the job's
 * {@code max_attempts}).
 */
@Service
public class IngestionJobRunner {

    private static final Logger log = LoggerFactory.getLogger(IngestionJobRunner.class);

    private final IngestionJobStore store;
    private final IngestionOrchestrator orchestrator;
    private final EntityEnrichmentService enrichment;
    private final TransactionOperations transactions;
    private final JsonMapper json;
    private final Duration retryBackoff;
    private final Duration enrichDelay;

    public IngestionJobRunner(IngestionJobStore store,
                              IngestionOrchestrator orchestrator,
                              EntityEnrichmentService enrichment,
                              TransactionOperations transactions,
                              JsonMapper json,
                              @Value("${orbit.ingestion.jobs.retry-backoff:PT30S}") Duration retryBackoff,
                              @Value("${orbit.ingestion.jobs.enrich-delay:PT3S}") Duration enrichDelay) {
        this.store = store;
        this.orchestrator = orchestrator;
        this.enrichment = enrichment;
        this.transactions = transactions;
        this.json = json;
        this.retryBackoff = retryBackoff;
        this.enrichDelay = enrichDelay;
    }

    public void run(IngestionJob job) {
        log.info("Ingestion job #{} ({} {}) attempt {}/{}", job.id(), job.kind(),
                job.sourceType() != null ? job.sourceType() : "#" + job.entityId(), job.attempts(), job.maxAttempts());
        try {
            switch (job.kind()) {
                case IngestionJob.SOURCE -> runSource(job);
                case IngestionJob.RESYNC -> runResync(job);
                case IngestionJob.ENRICH -> {
                    enrichment.enrich(job.entityId());
                    store.succeed(job.id(), job.entityId(), null);
                }
                default -> store.fail(job.id(), "Unknown job kind " + job.kind());
            }
        } catch (Exception e) {
            String error = e.getClass().getSimpleName() + ": " + e.getMessage();
            if (retryable(e) && job.attempts() < job.maxAttempts()) {
                Duration delay = backoff(job.attempts());
                log.warn("Ingestion job #{} failed, retrying in {}s: {}", job.id(), delay.toSeconds(), error);
                store.retryLater(job.id(), error, delay);
            } else {
                log.error("Ingestion job #{} failed for good: {}", job.id(), error, e);
                store.fail(job.id(), error);
            }
        }
    }

    private void runSource(IngestionJob job) throws IOException {
        SourceRef ref = json.readValue(job.sourceRefJson(), SourceRef.class);
        Path upload = null;
        try {
            if (job.payload() != null) {
                upload = Files.createTempFile("orbit-upload-", ".pdf");
                Files.write(upload, job.payload());
                Map<String, String> context = new HashMap<>(ref.context());
                context.put("file_path", upload.toString());
                ref = new SourceRef(ref.url(), ref.sourceType(), ref.provenance(), ref.consentBasis(),
                        context, ref.ownerId(), ref.onboardingDraft(), ref.profileMode());
            }
            List<IngestionResult> results = orchestrator.ingestMulti(ref, false);
            Long entityId = IngestionOrchestrator.firstEntityId(results);
            String summary = json.writeValueAsString(IngestionSummary.of(ref.sourceType(), results));
            // One transaction: a finished source and the enrichment it triggers
            // become visible together, so a poll never sees "done" in between.
            transactions.executeWithoutResult(status -> {
                store.succeed(job.id(), entityId, summary);
                if (entityId != null) store.armEnrichment(job.ownerId(), entityId, enrichDelay);
            });
        } finally {
            if (upload != null) Files.deleteIfExists(upload);
        }
    }

    private void runResync(IngestionJob job) {
        // resyncEntity enriches once at the end itself.
        List<IngestionResult> results = orchestrator.resyncEntity(job.entityId());
        store.succeed(job.id(), job.entityId(), json.writeValueAsString(IngestionSummary.of("resync", results)));
    }

    /**
     * Whether another attempt could succeed. Invalid input and broken invariants
     * fail at once; provider outages, timeouts and database hiccups are retried.
     */
    static boolean retryable(Exception e) {
        return !(e instanceof IllegalArgumentException
                || e instanceof IllegalStateException
                || e instanceof JacksonException);
    }

    /** {@code retryBackoff × 4^(attempt-1)}: 30 s, 2 min, 8 min with the default. */
    Duration backoff(int attempt) {
        return retryBackoff.multipliedBy((long) Math.pow(4, Math.max(0, attempt - 1)));
    }
}
