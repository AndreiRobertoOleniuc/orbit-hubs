package com.orbit.app.controllers.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Structured availability read model — drives the availability pill and the
 * availabilityOverlap re-rank signal (reference §6.3).
 */
public record AvailabilityDto(
        boolean openToRequests,
        LocalDate availableFrom,
        LocalDate availableTo,
        Integer pensumPct,
        String note,
        List<String> preferredCollaboration) {
}
