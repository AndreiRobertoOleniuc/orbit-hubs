package com.orbit.auth.internal;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Configures the resource-server JWT decoder for Clerk-issued session tokens.
 *
 * <p>
 * Spring resolves the JWKS from the issuer's
 * {@code .well-known/jwks.json} endpoint once and caches the keys, so
 * per-request validation is networkless. A custom validator asserts the
 * {@code azp} claim equals the configured frontend origin.
 */
@Configuration
class ClerkJwtDecoderConfig {

    @Bean
    JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            @Value("${clerk.azp-origin}") String azpOrigin) {

        String jwkSetUri = issuerUri.endsWith("/") ? issuerUri + ".well-known/jwks.json"
                : issuerUri + "/.well-known/jwks.json";

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();

        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuerUri);
        OAuth2TokenValidator<Jwt> withAzp = new ClerkAzpValidator(azpOrigin);

        decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(List.of(withIssuer, withAzp)));
        return decoder;
    }

}
