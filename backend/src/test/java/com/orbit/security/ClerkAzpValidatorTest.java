package com.orbit.security;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;

class ClerkAzpValidatorTest {

    private final ClerkAzpValidator validator = new ClerkAzpValidator("https://orbit.example");

    @Test
    void exactAuthorizedPartyPasses() {
        assertThat(validator.validate(jwt("https://orbit.example")).hasErrors()).isFalse();
    }

    @Test
    void missingOrDifferentAuthorizedPartyFails() {
        assertThat(validator.validate(jwt(null)).hasErrors()).isTrue();
        assertThat(validator.validate(jwt("https://evil.example")).hasErrors()).isTrue();
    }

    private static Jwt jwt(String azp) {
        Map<String, Object> claims = azp == null ? Map.of("sub", "user")
                : Map.of("sub", "user", "azp", azp);
        return new Jwt("token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "none"), claims);
    }
}
