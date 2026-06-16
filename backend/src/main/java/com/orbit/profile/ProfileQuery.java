package com.orbit.profile;

/**
 * Read-side access to profiles for downstream modules (e.g., discovery).
 * The implementation is hidden in {@code com.orbit.profile.internal}.
 */
public interface ProfileQuery {

    /**
     * Idempotently create or refresh a user row from an external identity token.
     *
     * @param userId      the stable external identifier (e.g. Clerk {@code sub})
     * @param email       the user's current email address
     * @param displayName the user's current display name
     */
    void provisionUser(String userId, String email, String displayName);

    /**
     * Checks whether the given entity is owned by the user.
     *
     * @param entityId the entity identifier
     * @param userId   the owner identifier
     * @return true when {@code entities.owner_user_id = userId}
     */
    boolean isOwnedBy(String entityId, String userId);

}
