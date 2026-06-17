package com.orbit.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Validates that the {@code azp} claim of a Clerk session token matches the
 * allowed frontend origin.
 */
public record ClerkAzpValidator(String expectedAzp) implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error AZP_ERROR = new OAuth2Error("invalid_token",
            "The azp claim is missing or does not match the authorized frontend origin", null);

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        String azp = token.getClaimAsString("azp");
        if (expectedAzp.equals(azp)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(AZP_ERROR);
    }

}
