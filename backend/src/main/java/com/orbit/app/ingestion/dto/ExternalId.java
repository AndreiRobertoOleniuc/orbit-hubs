package com.orbit.app.ingestion.dto;

/**
 * An external identifier for entity resolution.
 * <p>
 * Examples: {@code ("github_login", "torvalds")}, {@code ("orcid", "0000-0001-2345-5678")},
 * {@code ("domain", "johndoe.dev")}.
 */
public record ExternalId(String idType, String idValue) {
}