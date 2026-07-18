package com.orbit.app.controllers.dto;

import java.time.Instant;
import java.util.List;

import tools.jackson.databind.JsonNode;

/**
 * Entity DTO for the entity profile API — the full profile read model with
 * nested sources, tags, and the supply-side signals (availability, affiliation,
 * languages, location, hubs, home region).
 * <p>
 * {@code claimed} — whether a user owns this profile. Unclaimed (harvested)
 * profiles can't receive contact requests (§11); the UI disables the button.
 */
public record EntityDto(
        Long id,
        String entityType,
        String displayName,
        String headline,
        String orgName,
        String summary,
        String status,
        String profileMode,
        JsonNode metadata,
        Instant createdAt,
        List<SourceDto> sources,
        List<TagDto> tags,
        AvailabilityDto availability,
        String affiliationKind,
        List<String> languages,
        String locationLabel,
        List<HubDto> hubs,
        RegionDto homeRegion,
        OrgLinkDto orgLink,
        boolean claimed) {
}
