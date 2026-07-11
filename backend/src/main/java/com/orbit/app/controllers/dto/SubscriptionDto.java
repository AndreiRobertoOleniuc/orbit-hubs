package com.orbit.app.controllers.dto;

/**
 * A subscribed tag ("Abonniertes Thema") — reference §9.
 */
public record SubscriptionDto(
        Long id,
        String slug,
        String label) {
}
