package com.orbit.app.controllers.dto;

import java.time.Instant;
import java.util.List;

/**
 * One distributed request in the Postfach — the "Verteilt, weil dein Profil
 * die Tags … trägt" entry (§8.2). {@code reason} is {@code profile_match} or
 * {@code subscription}; {@code matchedTags} carries the tag labels behind it.
 */
public record InboxDistributionDto(
        Long requestId,
        String title,
        String queryText,
        Long authorEntityId,
        String authorName,
        String authorOrgName,
        String reason,
        List<String> matchedTags,
        String wideningLevel,
        String requestStatus,
        Instant notifiedAt
) {}
