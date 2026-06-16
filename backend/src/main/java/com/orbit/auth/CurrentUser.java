package com.orbit.auth;

import java.util.Optional;

/**
 * Public, published gate for security-aware code to obtain the currently
 * authenticated Clerk-mapped user without reaching into implementation details.
 */
public interface CurrentUser {

    Optional<String> userId();

    Optional<String> email();

    boolean owns(String entityId);

}
