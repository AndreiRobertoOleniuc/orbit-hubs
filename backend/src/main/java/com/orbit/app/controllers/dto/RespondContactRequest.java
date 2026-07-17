package com.orbit.app.controllers.dto;

/**
 * Body of POST /api/v1/contacts/{id}/respond — the recipient's one-time
 * handshake response (§11).
 *
 * @param accept  {@code true} → accepted (a {@code message} is required);
 *                {@code false} → declined (no message)
 * @param message the reply message, required when accepting
 */
public record RespondContactRequest(
        boolean accept,
        String message
) {}
