package com.orbit.app.ingestion.dto;

/**
 * Result of an ingestion run for a single source.
 */
public record IngestionResult(
        Long entityId,
        Long sourceId,
        int chunkCount,
        IngestionStatus status
) {
    public static IngestionResult ingested(Long entityId, Long sourceId, int chunkCount) {
        return new IngestionResult(entityId, sourceId, chunkCount, IngestionStatus.INGESTED);
    }

    public static IngestionResult skipped(Long entityId) {
        return new IngestionResult(entityId, null, 0, IngestionStatus.SKIPPED);
    }

    public static IngestionResult error(Long entityId) {
        return new IngestionResult(entityId, null, 0, IngestionStatus.ERROR);
    }
}
