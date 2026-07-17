package com.orbit.app.controllers.dto;

import java.time.Instant;

/**
 * One outgoing contact request in the Postfach "Gesendet" — who I contacted,
 * my message, and the handshake outcome. {@code toEmail} and
 * {@code replyMessage} are populated only once the recipient has
 * {@code accepted} (mutual opt-in, §11).
 */
public record InboxSentContactDto(
        Long id,
        Long toEntityId,
        String toName,
        String toOrgName,
        String toEmail,
        String message,
        String status,
        String replyMessage,
        Long requestId,
        String requestTitle,
        Instant respondedAt,
        Instant createdAt
) {}
