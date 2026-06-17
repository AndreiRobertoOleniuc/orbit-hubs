package com.orbit.app.profile;

import com.orbit.domain.repository.EntityRepository;
import com.orbit.domain.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Supply-side application service. Replaces the former {@code ProfileQuery} published interface
 * and its {@code JdbcProfileQuery} implementation; downstream features inject it directly.
 */
@Service
public class ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

    private final UserRepository users;
    private final EntityRepository entities;

    ProfileService(UserRepository users, EntityRepository entities) {
        this.users = users;
        this.entities = entities;
    }

    /** Idempotently create or refresh a user row from an external identity token. */
    @Transactional
    public void provisionUser(String userId, String email, String displayName) {
        if (email == null) {
            log.warn("Provisioning user {} from a token without an email claim", userId);
        }
        users.upsert(userId, email, displayName);
    }

    /** Checks whether {@code entities.owner_user_id = userId}. */
    @Transactional(readOnly = true)
    public boolean isOwnedBy(String entityId, String userId) {
        return entities.existsByIdAndOwner_Id(entityId, userId);
    }
}
