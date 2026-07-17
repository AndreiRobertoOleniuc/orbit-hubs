package com.orbit.app.controllers.dto;

import java.time.Instant;

/** Response of POST /api/v1/contacts — the brokered handoff. */
public record ContactRequestDto(
        Long id,
        Long fromEntityId,
        Long toEntityId,
        Long requestId,
        String message,
        Instant createdAt
) {}
