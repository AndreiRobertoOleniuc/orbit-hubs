package com.orbit.app.controllers.dto;

/**
 * A tag reference for autocomplete — id, slug, and display label from the
 * controlled vocabulary. Used by the tag search endpoint.
 */
public record TagRefDto(
        Long id,
        String slug,
        String label) {
}
