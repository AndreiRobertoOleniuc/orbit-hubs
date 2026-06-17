package com.orbit.app.auth;

import java.util.Optional;

import com.orbit.app.profile.ProfileService;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Resolves the currently authenticated Clerk-mapped user from the security
 * context and delegates ownership checks to the profile service.
 */
@Component
class CurrentUserImpl implements CurrentUser {

    private final ProfileService profileService;

    CurrentUserImpl(ProfileService profileService) {
        this.profileService = profileService;
    }

    @Override
    public Optional<String> userId() {
        return jwt().map(Jwt::getSubject);
    }

    @Override
    public Optional<String> email() {
        return jwt().map(token -> token.getClaimAsString("email"));
    }

    @Override
    public boolean owns(String entityId) {
        return userId()
                .map(userId -> profileService.isOwnedBy(entityId, userId))
                .orElse(false);
    }

    private Optional<Jwt> jwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return Optional.of(jwtAuth.getToken());
        }
        return Optional.empty();
    }

}
