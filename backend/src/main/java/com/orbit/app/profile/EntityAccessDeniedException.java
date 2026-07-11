package com.orbit.app.profile;

/**
 * Thrown by {@link EntityService} when an entity exists but the current
 * authenticated user is not its owner. Mapped to HTTP 403 by
 * {@code EntityController}'s {@code @ExceptionHandler}.
 */
public class EntityAccessDeniedException extends RuntimeException {
    public EntityAccessDeniedException() {
        super("Entity does not belong to the current user");
    }
}