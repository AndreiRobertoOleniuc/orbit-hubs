package com.orbit.app.util;

import java.util.Locale;

/**
 * Shared text normalization utilities used across ingestion, enrichment, and controllers.
 */
public final class SlugUtils {

    private SlugUtils() {}

    /**
     * Normalize a tag label into a URL-safe slug for the controlled vocabulary.
     * E.g. "C/C++" → "c-cpp", "Embedded Systems" → "embedded-systems".
     */
    public static String slugify(String label) {
        if (label == null) return "";
        return label.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[+]+", "plus")
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("[\\s-]+", "-")
                .replaceAll("^-+|-+$", "");
    }
}