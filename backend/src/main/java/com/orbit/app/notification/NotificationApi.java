package com.orbit.app.notification;

/**
 * Published entry point for fan-out notifications triggered by distributions
 * and incoming contact events.
 */
public interface NotificationApi {

    default void fanOut() {
        throw new UnsupportedOperationException("not implemented");
    }

}
