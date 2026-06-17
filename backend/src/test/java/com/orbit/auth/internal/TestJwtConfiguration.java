package com.orbit.auth.internal;

import java.util.List;
import java.util.UUID;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;

import com.orbit.security.ClerkAzpValidator;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Test-only JWT decoder/encoder pair backed by a local, throw-away RSA key.
 *
 * <p>
 * This configuration overrides the production Clerk JWKS decoder so that
 * authentication tests can run offline. It reuses the same {@link ClerkAzpValidator}
 * so that origin validation is exercised exactly as in production.
 */
@Configuration
class TestJwtConfiguration {

    private static final RSAKey RSA_KEY = generateKey();

    @Bean
    @Primary
    JwtDecoder testJwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            @Value("${clerk.azp-origin}") String azpOrigin) throws Exception {

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(RSA_KEY.toRSAPublicKey()).build();
        var withIssuer = JwtValidators.createDefaultWithIssuer(issuerUri);
        var withAzp = new ClerkAzpValidator(azpOrigin);
        decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(List.of(withIssuer, withAzp)));
        return decoder;
    }

    @Bean
    JwtEncoder testJwtEncoder() {
        var jwkSource = new ImmutableJWKSet<SecurityContext>(new JWKSet(RSA_KEY));
        return new NimbusJwtEncoder(jwkSource);
    }

    private static RSAKey generateKey() {
        try {
            return new RSAKeyGenerator(2048)
                    .keyID(UUID.randomUUID().toString())
                    .generate();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate test RSA key", ex);
        }
    }

}
