package com.orbit.app.controllers.dto;

/**
 * Response body for POST /api/v1/entities/{id}/resync — the refreshed entity
 * plus ingestion counts.
 */
public record ResyncResponse(EntityDto entity, int ingested, int skipped, int errors) {
}