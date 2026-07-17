package com.orbit.app.controllers.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Full request DTO — returned for single-request detail views.
 * <p>
 * {@code mine} is {@code true} when the caller is the request's creator (the
 * detail view then shows the owner lifecycle controls). Author fields mirror
 * {@link RequestSummaryDto}: identity resolved from the creator's entity
 * profile, {@code null} when none exists.
 * <p>
 * {@code distributionCount}, {@code wideningLevel} ("X Profile benachrichtigt"
 * + current escalation scope kind) and {@code distributions} (the notified
 * profiles with their reason) are owner-only — {@code null} when {@code mine}
 * is false.
 */
public record RequestDto(
        Long id,
        String title,
        String description,
        String queryText,
        String scopeKind,
        Long scopeHubId,
        String scopeHubName,
        Long scopeRegionId,
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
        boolean mine,
        Integer distributionCount,
        String wideningLevel,
        List<DistributedProfileDto> distributions,
        Instant createdAt
) {}
