package com.orbit.app.controllers.dto;

/**
 * Request body for PUT /api/v1/entities/{id}/mode — the profile mode
 * ('supply' | 'demand').
 */
public record UpdateModeRequest(String mode) {
}
