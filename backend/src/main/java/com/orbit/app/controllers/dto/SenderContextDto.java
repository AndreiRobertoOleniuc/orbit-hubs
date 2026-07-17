package com.orbit.app.controllers.dto;

/**
 * The frozen sender-context snapshot carried by a contact request (§11.2) —
 * captured from the sender's entity at send time so later profile edits don't
 * rewrite history. Also the JSON shape stored in
 * {@code contact_requests.sender_context}.
 */
public record SenderContextDto(
        String name,
        String role,
        String org,
        String description,
        String searchedText
) {}
