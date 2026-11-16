package com.orbit.app.ingestion.dto;

import java.util.List;

/**
 * What one ingestion input produced: per source the outcome, plus the totals the
 * onboarding and profile views show ("12 Abschnitte ausgewertet", "bereits aktuell").
 */
public record IngestionSummary(
        String sourceType,
        int sources,
        int ingested,
        int skipped,
        int errors,
        int totalChunks,
        List<IngestionResult> results) {

    public static IngestionSummary of(String sourceType, List<IngestionResult> results) {
        return new IngestionSummary(
                sourceType,
                results.size(),
                count(results, IngestionStatus.INGESTED),
                count(results, IngestionStatus.SKIPPED),
                count(results, IngestionStatus.ERROR),
                results.stream().mapToInt(IngestionResult::chunkCount).sum(),
                List.copyOf(results));
    }

    private static int count(List<IngestionResult> results, IngestionStatus status) {
        return (int) results.stream().filter(result -> result.status() == status).count();
    }
}
