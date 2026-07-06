package com.orbit.app.controllers.dto;

import java.util.Map;

/**
 * Request body for POST /api/v1/ingestion — a single source ingestion request.
 */
public record IngestionRequest(
        String sourceType,
        Map<String, String> context
) {}