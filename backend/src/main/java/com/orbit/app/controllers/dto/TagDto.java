package com.orbit.app.controllers.dto;

/**
 * Tag DTO for the entity profile API — one Kompetenz with its origin.
 */
public record TagDto(
        Long id,
        String slug,
        String label,
        String origin) {
}