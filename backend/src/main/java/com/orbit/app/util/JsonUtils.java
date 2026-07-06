package com.orbit.app.util;

/**
 * Shared JSON escaping utility for building raw JSON string bodies
 * (used by adapters that construct JSON for external APIs).
 */
public final class JsonUtils {

    private JsonUtils() {}

    /**
     * Escape a string for embedding in a JSON string value.
     */
    public static String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}