package com.orbit.app.controllers.dto;

import java.util.List;

/**
 * Request body for PUT /api/v1/entities/{id}/hubs — the confirmed set of hub
 * ids the entity belongs to. Replaces the entity's memberships (as 'confirmed').
 */
public record UpdateHubsRequest(List<Long> hubIds) {
}
