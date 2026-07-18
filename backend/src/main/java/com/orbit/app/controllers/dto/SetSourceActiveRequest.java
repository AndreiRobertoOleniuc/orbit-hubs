package com.orbit.app.controllers.dto;

/**
 * Request body for PATCH /api/v1/entities/{entityId}/sources/{sourceId} —
 * toggle a single source (repo / work) on or off for search (source curation).
 */
public record SetSourceActiveRequest(boolean active) {
}
