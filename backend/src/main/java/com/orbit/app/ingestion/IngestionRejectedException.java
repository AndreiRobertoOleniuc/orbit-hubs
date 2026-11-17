package com.orbit.app.ingestion;

/**
 * A source that must not be ingested for this user, with a German message the
 * user is shown as is: the source belongs to another account's profile, or the
 * account behind it has nothing to import. Not retryable: the ingestion job
 * fails at once ({@link IllegalArgumentException}).
 */
public class IngestionRejectedException extends IllegalArgumentException {

    public IngestionRejectedException(String userMessage) {
        super(userMessage);
    }
}
