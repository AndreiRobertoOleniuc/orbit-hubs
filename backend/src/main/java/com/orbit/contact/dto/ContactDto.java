package com.orbit.contact.dto;

import java.util.UUID;

/**
 * Placeholder DTO for contact hand-off data shared with other modules.
 */
public record ContactDto(UUID requestId, String email) {
}
