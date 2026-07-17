package com.orbit.app.controllers.dto;

import java.util.List;

/**
 * Result of the contact-gating check (§11.1) — evaluated for the current user
 * BEFORE the send, so the contact dialog can show inline what's missing
 * instead of silently disabling the button.
 * <p>
 * {@code missing} check ids: {@code entity} (no own profile yet),
 * {@code email} (no verified email), {@code affiliation} (Zugehörigkeit not
 * set), {@code source} (no LinkedIn / website / own description source).
 */
public record ContactGatingDto(
        boolean allowed,
        List<String> missing
) {}
