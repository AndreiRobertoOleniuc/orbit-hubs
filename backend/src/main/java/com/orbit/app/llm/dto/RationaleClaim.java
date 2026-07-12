package com.orbit.app.llm.dto;

import java.util.List;

/**
 * A single grounded claim from the rationale generator: a statement plus the
 * citation ids that support it. Citation validation (§6.5) drops any claim whose
 * {@code sourceIds} are not all in the passed evidence set.
 */
public record RationaleClaim(
        String text,
        List<String> sourceIds) {
}
