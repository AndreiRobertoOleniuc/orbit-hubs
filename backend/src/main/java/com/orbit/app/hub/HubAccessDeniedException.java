package com.orbit.app.hub;

/**
 * Thrown by {@link HubService} when a hub-administration action is attempted by
 * a user who is not an admin. Mapped to HTTP 403 by
 * {@link com.orbit.app.controllers.GlobalExceptionHandler}.
 */
public class HubAccessDeniedException extends RuntimeException {
    public HubAccessDeniedException() {
        super("Hub administration requires an admin user");
    }
}
