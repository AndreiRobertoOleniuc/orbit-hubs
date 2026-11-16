package com.orbit.app.llm.dto;

/**
 * A freshly derived tag label and an existing tag it might duplicate — the input
 * of {@link com.orbit.app.llm.LlmSummaryService#sameTopics}.
 *
 * @param label    the new label
 * @param existing the label of an existing tag
 */
public record TopicPair(String label, String existing) {
}
