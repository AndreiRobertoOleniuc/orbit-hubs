package com.orbit.app.controllers.dto;

/**
 * Request body for PUT /api/v1/entities/{id}/summary — manual summary override.
 */
public record UpdateSummaryRequest(String summary) {
}