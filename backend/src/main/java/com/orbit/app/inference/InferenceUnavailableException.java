package com.orbit.app.inference;

/** Raised when a required managed inference model cannot produce a result. */
public class InferenceUnavailableException extends RuntimeException {

    public InferenceUnavailableException(String message) {
        super(message);
    }

    public InferenceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
