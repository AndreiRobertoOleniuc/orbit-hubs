package com.orbit.app.controllers.dto;

import java.util.List;

/**
 * Request body for PUT /api/v1/entities/{id}/subscriptions — the confirmed set
 * of subscribed tag labels ("Abonnierte Themen"). Tags are minted on demand.
 */
public record UpdateSubscriptionsRequest(List<String> tags) {
}
