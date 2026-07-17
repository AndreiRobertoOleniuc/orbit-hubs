package com.orbit.app.controllers.dto;

/**
 * Body of POST /api/v1/contacts — start a first-contact handoff (§11).
 *
 * @param toEntityId   recipient entity (must be a claimed profile)
 * @param requestId    set when initiated from a board request (auto-transitions
 *                     it to {@code contacted})
 * @param message      the personal message — required, non-empty
 * @param searchedText what the sender searched for (search flow); when null and
 *                     {@code requestId} is set, the request's query text is used
 */
public record CreateContactRequest(
        Long toEntityId,
        Long requestId,
        String message,
        String searchedText
) {}
