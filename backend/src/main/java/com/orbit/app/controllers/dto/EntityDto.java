package com.orbit.app.controllers.dto;

import java.time.Instant;
import java.util.List;

import tools.jackson.databind.JsonNode;

/**
 * Entity DTO for the entity profile API — the full profile read model
 * with nested sources and tags.
 */
public record EntityDto(
        Long id,
        String entityType,
        String displayName,
        String headline,
        String orgName,
        String summary,
        String status,
        JsonNode metadata,
        Instant createdAt,
        List<SourceDto> sources,
        List<TagDto> tags) {
}