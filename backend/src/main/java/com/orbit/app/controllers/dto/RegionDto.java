package com.orbit.app.controllers.dto;

/**
 * Region read model — a node in the Hub → Region → CH tree (reference §5).
 */
public record RegionDto(
        Long id,
        String slug,
        String name,
        Long parentRegionId) {
}
