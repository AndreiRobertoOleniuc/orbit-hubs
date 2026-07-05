package com.orbit.app.ingestion;

/**
 * Thrown when a source adapter fails to fetch or process content.
 */
public class IngestionException extends RuntimeException {

    public IngestionException(String message) {
        super(message);
    }

    public IngestionException(String message, Throwable cause) {
        super(message, cause);
    }
}