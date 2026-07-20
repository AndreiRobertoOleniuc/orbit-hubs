package com.orbit.app.controllers.dto;

import java.util.List;

/**
 * Request body for PUT /api/v1/entities/{id}/profile-meta — title/role,
 * workplace, affiliation, working languages, and human-readable location.
 * <p>
 * {@code headline} (Titel / Rolle) and {@code orgName} (Arbeitgeber) let a user
 * correct the auto-derived values, which are often wrong for harvested profiles
 * (OpenAlex has no job title and its last-known institution is frequently stale).
 * Manual edits survive re-harvest — re-ingestion never overwrites an existing
 * entity's headline/org.
 */
public record UpdateProfileMetaRequest(
        String displayName,
        String headline,
        String orgName,
        String affiliationKind,
        List<String> languages,
        String locationLabel) {
}
