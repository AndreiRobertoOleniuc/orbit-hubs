package com.orbit.app.controllers.dto;

/**
 * Request body for admin hub create/edit. {@code slug} is optional — when blank
 * it is derived from {@code name}. {@code regionId} must reference an existing
 * region.
 */
public record HubMutationRequest(
        String name,
        String slug,
        String hubType,
        Long regionId) {
}
