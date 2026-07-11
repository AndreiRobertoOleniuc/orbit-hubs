package com.orbit.app.controllers.dto;

import java.util.List;

/**
 * Request body for PUT /api/v1/entities/{id}/profile-meta — affiliation,
 * working languages, and human-readable location.
 */
public record UpdateProfileMetaRequest(
        String affiliationKind,
        List<String> languages,
        String locationLabel) {
}
