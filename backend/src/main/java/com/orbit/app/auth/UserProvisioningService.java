package com.orbit.app.auth;

import com.orbit.app.profile.ProfileService;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Just-in-time provisioning of local {@code users} rows from verified Clerk JWTs.
 *
 * <p>
 * The service is invoked on every authenticated request. It delegates to the
 * profile service to upsert the local user row.
 */
@Component
public class UserProvisioningService {

    private final ProfileService profileService;

    public UserProvisioningService(ProfileService profileService) {
        this.profileService = profileService;
    }

    public void provision(Jwt token) {
        String userId = token.getSubject();
        String email = token.getClaimAsString("email");
        String displayName = token.getClaimAsString("name");
        profileService.provisionUser(userId, email, displayName);
    }

}
