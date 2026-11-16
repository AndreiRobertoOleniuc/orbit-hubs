package com.orbit.app.search.dto;

/**
 * Request for the rationale of one search result.
 *
 * @param query    the search exactly as it was sent to {@code POST /search}
 * @param entityId the result to explain
 */
public record SearchRationaleRequest(String query, Long entityId) {
}
