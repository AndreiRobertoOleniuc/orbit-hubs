package com.orbit.app.ingestion.linkedin.dto;

/**
 * The name block extracted from a LinkedIn PDF — name, headline, and location.
 * Found between the last sidebar section and the first main section.
 */
public record NameBlock(String name, String headline, String location) {
}