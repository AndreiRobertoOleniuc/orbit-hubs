package com.orbit.app.controllers.dto;

import java.time.Instant;

import com.orbit.app.ingestion.dto.IngestionSummary;

/**
 * An ingestion job as its owner sees it. Ingestion endpoints answer
 * {@code 202 Accepted} with this, and {@code GET /ingestion/jobs} reports progress.
 *
 * @param kind       {@code source}, {@code resync} or {@code enrich}
 * @param sourceType the submitted source type ({@code null} for resync / enrich)
 * @param status     {@code queued}, {@code running}, {@code succeeded} or {@code failed}
 * @param attempts   runs started so far; a transient failure is retried with backoff
 * @param entityId   the profile the job worked on, once known
 * @param result     per-source outcome of a succeeded source or resync job
 * @param error      why the last run failed, when it did
 */
public record IngestionJobDto(
        Long id,
        String kind,
        String sourceType,
        String status,
        int attempts,
        Long entityId,
        IngestionSummary result,
        String error,
        Instant createdAt,
        Instant finishedAt) {
}
