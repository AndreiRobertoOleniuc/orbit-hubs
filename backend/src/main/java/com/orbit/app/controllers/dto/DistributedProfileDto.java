package com.orbit.app.controllers.dto;

import java.time.Instant;
import java.util.List;

/**
 * One profile a request was distributed to — shown to the request's creator
 * only (§8.2). Carries the explainable reason so the owner sees not just who
 * was notified but why ({@code profile_match} via tag overlap, or
 * {@code subscription} via an abonniertes Thema).
 */
public record DistributedProfileDto(
        Long entityId,
        String name,
        String orgName,
        String entityType,
        String reason,
        List<String> matchedTags,
        Instant notifiedAt
) {}
