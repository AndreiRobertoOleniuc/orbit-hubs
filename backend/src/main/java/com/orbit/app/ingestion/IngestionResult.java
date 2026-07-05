package com.orbit.app.ingestion;

import java.util.List;

/**
 * Result of an ingestion run for a single source.
 */
public record IngestionResult(
        Long entityId,
        Long sourceId,
        int chunkCount,
        Status status
) {
    public enum Status {
        INGESTED,   // new or updated source + chunks
        SKIPPED,    // content hash unchanged — no re-embedding needed
        ERROR       // fetch or processing failed
    }

    public static IngestionResult ingested(Long entityId, Long sourceId, int chunkCount) {
        return new IngestionResult(entityId, sourceId, chunkCount, Status.INGESTED);
    }

    public static IngestionResult skipped(Long entityId) {
        return new IngestionResult(entityId, null, 0, Status.SKIPPED);
    }

    public static IngestionResult error(Long entityId) {
        return new IngestionResult(entityId, null, 0, Status.ERROR);
    }
}