package com.orbit.contact;

/**
 * Published entry point for one-time email hand-off between parties.
 * The module depends on request and notification published interfaces.
 */
public interface ContactApi {

    default void handOff() {
        throw new UnsupportedOperationException("not implemented");
    }

}
