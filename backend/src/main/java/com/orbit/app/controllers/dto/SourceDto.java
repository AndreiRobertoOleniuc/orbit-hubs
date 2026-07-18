package com.orbit.app.controllers.dto;

import java.time.Instant;

/**
 * Source DTO for the entity profile API — one evidence feed.
 */
public record SourceDto(
        Long id,
        String sourceType,
        String label,
        String sourceUrl,
        String fetchStatus,
        Instant fetchedAt,
        boolean active) {
}