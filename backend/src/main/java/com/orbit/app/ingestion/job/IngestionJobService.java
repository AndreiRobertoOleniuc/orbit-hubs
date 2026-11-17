package com.orbit.app.ingestion.job;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Service;

import com.orbit.app.controllers.dto.IngestionJobDto;
import com.orbit.app.controllers.dto.IngestionJobsDto;
import com.orbit.app.ingestion.IngestionRejectedException;
import com.orbit.app.ingestion.dto.IngestionSummary;
import com.orbit.app.ingestion.dto.SourceRef;
import tools.jackson.databind.json.JsonMapper;

/**
 * Queues ingestion work and reports its progress. The work itself runs in
 * {@link IngestionJobWorker}; an HTTP request only writes the job row.
 */
@Service
public class IngestionJobService {

    private final IngestionJobStore store;
    private final JsonMapper json;

    public IngestionJobService(IngestionJobStore store, JsonMapper json) {
        this.store = store;
        this.json = json;
    }

    /**
     * Queue one submitted source.
     *
     * @param payload an uploaded file the adapter reads (written to a temporary
     *                file while the job runs, deleted with the job's finish), or null
     */
    public IngestionJobDto enqueueSource(SourceRef ref, byte[] payload) {
        long id = store.enqueueSource(ref.ownerId(), ref.sourceType(), json.writeValueAsString(ref), payload);
        return queued(id, IngestionJob.SOURCE, ref.sourceType(), null);
    }

    /** Queue a re-ingestion of every re-fetchable source of an entity. */
    public IngestionJobDto enqueueResync(String ownerId, long entityId) {
        long id = store.enqueueResync(ownerId, entityId);
        return queued(id, IngestionJob.RESYNC, null, entityId);
    }

    /** The caller's jobs among {@code ids}, and whether they and their enrichment are done. */
    public IngestionJobsDto jobs(String ownerId, Collection<Long> ids) {
        List<IngestionJob> jobs = store.findOwned(ownerId, ids);
        boolean settled = jobs.stream().allMatch(IngestionJob::finished) && !store.enrichmentPending(ownerId);
        return new IngestionJobsDto(jobs.stream().map(this::toDto).toList(), settled);
    }

    IngestionJobDto toDto(IngestionJob job) {
        IngestionSummary result = job.resultJson() == null ? null
                : json.readValue(job.resultJson(), IngestionSummary.class);
        return new IngestionJobDto(job.id(), job.kind(), job.sourceType(), job.status(), job.attempts(),
                job.entityId(), result, job.error(), userMessage(job.error()), job.createdAt(), job.finishedAt());
    }

    /** The user-facing reason of a rejected source; the runner stores it as {@code Class: message}. */
    static String userMessage(String error) {
        String prefix = IngestionRejectedException.class.getSimpleName() + ": ";
        return error != null && error.startsWith(prefix) ? error.substring(prefix.length()) : null;
    }

    private static IngestionJobDto queued(long id, String kind, String sourceType, Long entityId) {
        return new IngestionJobDto(id, kind, sourceType, IngestionJob.QUEUED, 0, entityId, null, null, null, null, null);
    }
}
