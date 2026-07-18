package com.orbit.app.controllers.dto;

/**
 * A resolved Person→Organisation link for the profile header (§10) — the org
 * entity a person is linked to. Present only when a link row exists; the UI
 * renders the org name as a link to its profile. {@code hrVerified} reflects the
 * org's Handelsregister (Zefix) source.
 */
public record OrgLinkDto(
        Long entityId,
        String name,
        boolean hrVerified) {
}
