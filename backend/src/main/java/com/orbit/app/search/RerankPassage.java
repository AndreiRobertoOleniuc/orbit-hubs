package com.orbit.app.search;

/**
 * One text the cross-encoder scores against the query.
 *
 * @param label what the passage is, for the search trace (a source label, or
 *              {@link #CARD_LABEL} for the profile card)
 * @param text  the text as sent to the model
 */
public record RerankPassage(String label, String text) {

    public static final String CARD_LABEL = "profile card";
}
