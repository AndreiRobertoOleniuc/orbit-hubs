package com.orbit.app.ingestion.openalex.dto;

/**
 * OpenAlex work (publication) data. Each work becomes one source.
 */
public record OpenAlexWork(
        String openalexId,
        String title,
        int publicationYear,
        String doi,
        String type,
        int citedByCount,
        String primaryTopic,
        String reconstructedAbstract,
        /** The ingested author's position on the work: first, middle or last (null if unknown). */
        String authorPosition
) {}