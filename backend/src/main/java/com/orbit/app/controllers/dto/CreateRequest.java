package com.orbit.app.controllers.dto;

import java.time.LocalDate;
import java.util.List;

/** Request body for creating a new request. */
public record CreateRequest(
        String title,
        String description,
        String queryText,
        ScopeSelection scope,
        String urgency,
        LocalDate deadline,
        LocalDate wantFrom,
        LocalDate wantTo,
        Integer wantPensumPct,
        List<String> tags
) {
    public record ScopeSelection(String kind, Long hubId, Long regionId) {}
}