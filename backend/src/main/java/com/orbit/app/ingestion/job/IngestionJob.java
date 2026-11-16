package com.orbit.app.ingestion.job;

import java.time.Instant;

/**
 * One row of {@code ingestion_jobs} (V20).
 *
 * @param kind          {@code source}, {@code resync} or {@code enrich}
 * @param sourceRefJson the submitted {@link com.orbit.app.ingestion.dto.SourceRef} (source jobs)
 * @param payload       an uploaded PDF, until the job has finished
 * @param attempts      runs started so far, including the current one
 * @param resultJson    the {@link com.orbit.app.ingestion.dto.IngestionSummary} of a finished job
 */
public record IngestionJob(
        long id,
        String ownerId,
        String kind,
        String sourceType,
        String sourceRefJson,
        byte[] payload,
        Long entityId,
        String status,
        int attempts,
        int maxAttempts,
        String resultJson,
        String error,
        Instant createdAt,
        Instant finishedAt) {

    public static final String SOURCE = "source";
    public static final String RESYNC = "resync";
    public static final String ENRICH = "enrich";

    public static final String QUEUED = "queued";
    public static final String RUNNING = "running";
    public static final String SUCCEEDED = "succeeded";
    public static final String FAILED = "failed";

    public boolean finished() {
        return SUCCEEDED.equals(status) || FAILED.equals(status);
    }
}
