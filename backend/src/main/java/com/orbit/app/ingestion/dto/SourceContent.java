package com.orbit.app.ingestion.dto;

/**
 * A source's high-signal core content with metadata, passed to the LLM for
 * entity-level competence enrichment (summary generation + tag derivation).
 *
 * @param sourceType the source_type string (e.g. "github", "zefix", "personal_site")
 * @param label      the human-readable citation chip text
 * @param coreText   the high-signal text that was chunked and embedded
 */
public record SourceContent(
        String sourceType,
        String label,
        String coreText
) {
}