package com.orbit.notification.dto;

import java.util.UUID;

/**
 * Placeholder DTO for notification payloads shared with other modules.
 */
public record NotificationDto(UUID recipientId, String message) {
}
