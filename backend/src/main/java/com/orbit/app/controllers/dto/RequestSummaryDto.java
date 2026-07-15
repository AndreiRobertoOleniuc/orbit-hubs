package com.orbit.app.controllers.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Compact request summary — returned for board lists and "My Requests".
 * <p>
 * Carries the fields the board card renders: urgency + deadline (countdown),
 * the want-window ("Aug – Okt · ~60 %"), and the author identity resolved from
 * the creator's entity profile (design: PostAuthor chip linking to the
 * profile). Author fields are {@code null} when the creator has no profile.
 */
public record RequestSummaryDto(
        Long id,
        String title,
        String description,
        String queryText,
        String scopeKind,
        String scopeHubName,
        String scopeRegionName,
        String urgency,
        LocalDate deadline,
        String status,
        LocalDate wantFrom,
        LocalDate wantTo,
        Integer wantPensumPct,
        List<TagRefDto> tags,
        Long authorEntityId,
        String authorName,
        String authorOrgName,
        String authorType,
        Instant createdAt
) {}
