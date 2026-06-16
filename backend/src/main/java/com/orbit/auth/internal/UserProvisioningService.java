package com.orbit.auth.internal;

import com.orbit.profile.ProfileQuery;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Just-in-time provisioning of local {@code users} rows from verified Clerk JWTs.
 *
 * <p>
 * The service is invoked on every authenticated request. It delegates to the
 * profile module's published interface so that auth never reaches into another
 * module's internals.
 */
@Component
class UserProvisioningService {

    private final ProfileQuery profileQuery;

    UserProvisioningService(ProfileQuery profileQuery) {
        this.profileQuery = profileQuery;
    }

    void provision(Jwt token) {
        String userId = token.getSubject();
        String email = token.getClaimAsString("email");
        String displayName = token.getClaimAsString("name");
        profileQuery.provisionUser(userId, email, displayName);
    }

}
