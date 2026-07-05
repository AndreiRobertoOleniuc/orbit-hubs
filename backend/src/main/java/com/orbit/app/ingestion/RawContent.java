package com.orbit.app.ingestion;

import java.time.Instant;
import java.util.Map;

/**
 * Raw content fetched from an external system — the bytes/text before extraction.
 */
public record RawContent(
        String text,                    // the full raw text/JSON
        Map<String, Object> metadata,   // source-specific structured data
        Instant fetchedAt
) {
}