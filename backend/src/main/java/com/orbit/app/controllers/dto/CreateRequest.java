package com.orbit.app.controllers.dto;

import java.time.LocalDate;
import java.util.List;

/** Request body for creating a new request. */
public record CreateRequest(
        String title,
        String description,
        String queryText,
        RequestScopeSelection scope,
        String urgency,
        LocalDate deadline,
        LocalDate wantFrom,
        LocalDate wantTo,
        Integer wantPensumPct,
        List<String> tags
) {
}
