package com.orbit.app.profile;

import com.orbit.domain.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Supply-side application service. Owns just-in-time provisioning of the local {@code users}
 * row from verified identity tokens; downstream features inject it directly.
 */
@Service
public class ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

    private final UserRepository users;

    ProfileService(UserRepository users) {
        this.users = users;
    }

    /** Idempotently create or refresh a user row from an external identity token. */
    @Transactional
    public void provisionUser(String userId, String email, String displayName) {
        if (email == null) {
            log.warn("Provisioning user {} from a token without an email claim", userId);
        }
        users.upsert(userId, email, displayName);
    }
}
