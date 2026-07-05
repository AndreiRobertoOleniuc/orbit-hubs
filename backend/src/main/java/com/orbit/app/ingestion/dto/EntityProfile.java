package com.orbit.app.ingestion.dto;

import java.util.Map;

/**
 * Profile fields for a new entity — derived from the first source when no
 * existing entity is found.
 */
public record EntityProfile(
        String entityType,      // 'person' | 'organization'
        String displayName,
        String headline,
        String orgName,
        Double latitude,
        Double longitude,
        Map<String, Object> metadata
) {
}