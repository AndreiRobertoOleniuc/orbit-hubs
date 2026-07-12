package com.orbit.app.search.dto;

/**
 * A validated citation chip on a result card (reference §6.5). Points at a real
 * source that was actually retrieved for this match — a citation we didn't pass
 * to the generator can never reach a card.
 */
public record Citation(
        Long sourceId,
        String label,
        String sourceType,
        String sourceUrl) {
}
