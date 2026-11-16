package com.orbit.app.llm.dto;

/**
 * A search request prepared for the pipeline.
 *
 * @param english     the full request in English — embedded and sent to the lexical leg
 * @param searchTerms the condensed English search terms — what the cross-encoder scores against
 */
public record SearchQueryRewrite(String english, String searchTerms) {
}
