package com.orbit.app.controllers.dto;

/**
 * Request body for admin hub create/edit. {@code slug} is optional — when blank
 * it is derived from {@code name}.
 * <p>
 * The owning region is given either by {@code regionId} (an existing region) or,
 * when the admin picked a place that is not part of the region tree yet, by
 * {@code regionName} plus its optional coordinates — that region is then created
 * on demand under the CH root (reference §5).
 */
public record HubMutationRequest(
        String name,
        String slug,
        String hubType,
        Long regionId,
        String regionName,
        Double regionLatitude,
        Double regionLongitude) {
}
