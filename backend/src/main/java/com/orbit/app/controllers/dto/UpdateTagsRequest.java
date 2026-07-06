package com.orbit.app.controllers.dto;

import java.util.List;

/**
 * Request body for PUT /api/v1/entities/{id}/tags — the user's confirmed tag set.
 */
public record UpdateTagsRequest(List<String> tags) {
}