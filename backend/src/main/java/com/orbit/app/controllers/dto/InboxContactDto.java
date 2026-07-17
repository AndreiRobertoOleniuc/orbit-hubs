package com.orbit.app.controllers.dto;

import java.time.Instant;

/**
 * One incoming contact request in the Postfach "Erhalten" — "wer fragt", the
 * frozen sender context, the personal message, and the recipient's own
 * handshake response. {@code fromEmail} is revealed only once this request is
 * {@code accepted} (mutual opt-in, §11).
 */
public record InboxContactDto(
        Long id,
        Long fromEntityId,
        String fromName,
        String fromOrgName,
        String fromEmail,
        SenderContextDto senderContext,
        String message,
        Long requestId,
        String requestTitle,
        String status,
        String replyMessage,
        Instant respondedAt,
        Instant createdAt
) {}
