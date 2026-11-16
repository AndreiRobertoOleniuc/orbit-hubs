package com.orbit.app.admin;

/**
 * Thrown when an administration action is attempted by a user who is not an
 * admin. Mapped to HTTP 403 by
 * {@link com.orbit.app.controllers.GlobalExceptionHandler}.
 */
public class AdminAccessDeniedException extends RuntimeException {
    public AdminAccessDeniedException() {
        super("This action requires an admin user");
    }
}
