package com.orbit.app.controllers.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Request body for PUT /api/v1/entities/{id}/availability.
 */
public record UpdateAvailabilityRequest(
        boolean openToRequests,
        LocalDate availableFrom,
        LocalDate availableTo,
        Integer pensumPct,
        String note,
        List<String> preferredCollaboration) {
}
