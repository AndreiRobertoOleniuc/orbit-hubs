package com.orbit.onboarding.dto;

import java.util.UUID;

/**
 * Placeholder DTO for onboarding commands shared with other modules.
 */
public record OnboardingDto(UUID userId, String sourceUrl) {
}
