package com.orbit.app.ingestion.openalex.dto;

/**
 * OpenAlex author profile data fetched from the API.
 * Used for entity creation during ingestion.
 */
public record OpenAlexAuthor(
        String openalexId,
        String displayName,
        String orcid,
        String affiliation,
        int worksCount
) {}