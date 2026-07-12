package com.orbit.app.llm.dto;

/**
 * One piece of evidence handed to the grounded-rationale generator (reference
 * §6.4). {@code id} is the stable citation id (S1, S2, …) the model must cite;
 * {@code text} is the winning chunk content for the query.
 */
public record RationaleEvidence(
        String id,
        String type,
        String label,
        String text) {
}
