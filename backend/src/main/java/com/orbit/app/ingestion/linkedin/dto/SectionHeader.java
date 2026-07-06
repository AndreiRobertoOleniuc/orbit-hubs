package com.orbit.app.ingestion.linkedin.dto;

/**
 * A detected section header in a LinkedIn PDF, with its canonical key and line index.
 */
public record SectionHeader(String headerText, String sectionKey, int lineIndex) {
}