package com.orbit.app.ingestion;

/**
 * A single chunk segment produced by the chunking service.
 */
public record ChunkSegment(
        String text,
        String section,      // context label (e.g. 'Features', 'Embedded Systems')
        int tokenCount
) {
}