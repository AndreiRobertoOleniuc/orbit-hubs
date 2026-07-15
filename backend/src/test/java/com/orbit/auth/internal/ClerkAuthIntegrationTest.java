package com.orbit.auth.internal;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Clerk-style JWT authentication and just-in-time user
 * provisioning.
 *
 * <p>
 * Uses a local JWT encoder/decoder so the tests run without reaching Clerk's
 * servers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestJwtConfiguration.class)
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost",
        "clerk.azp-origin=http://localhost:5173"
})
class ClerkAuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private DataSource dataSource;

    private JdbcClient jdbc;

    @BeforeEach
    void setUp() {
        jdbc = JdbcClient.create(dataSource);
        jdbc.sql("DELETE FROM requests WHERE created_by LIKE 'user_test_%'").update();
        jdbc.sql("DELETE FROM users WHERE id LIKE 'user_test_%'").update();
    }

    @Test
    void publicRouteIsReachableWithoutToken() throws Exception {
        // /api/v1/board is a whitelisted public route — 200 (not 401) without a token
        mockMvc.perform(get("/api/v1/board"))
                .andExpect(status().isOk());
    }

    @Test
    void authenticatedUserCanCreateRequest() throws Exception {
        // Regression: CurrentUser must be constructor-injected in the service —
        // a CurrentUser handler-method parameter breaks all request routes.
        String token = token("user_test_req", "req@orbit.test", "Req", "http://localhost:5173");

        mockMvc.perform(post("/api/v1/requests")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "UXUI Expert", "queryText": "Ich suche einen UIUX Experten",
                                 "scope": {"kind": "all"}, "urgency": "flexible",
                                 "deadline": "2026-08-07", "wantFrom": "2026-07-30",
                                 "wantTo": "2026-08-22", "wantPensumPct": 20,
                                 "tags": ["UI", "UX"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queryText").value("Ich suche einen UIUX Experten"))
                .andExpect(jsonPath("$.status").value("open"))
                .andExpect(jsonPath("$.mine").value(true));
    }

    @Test
    void missingRequestIs404NotBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/requests/999999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void authenticatedRouteRequiresToken() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenCreatesAndReturnsUser() throws Exception {
        String token = token("user_test_1", "alice@orbit.test", "Alice", "http://localhost:5173");

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user_test_1"))
                .andExpect(jsonPath("$.email").value("alice@orbit.test"));

        assertUser("user_test_1", "alice@orbit.test", "Alice");
    }

    @Test
    void tokenWithWrongAzpIsRejected() throws Exception {
        String token = token("user_test_2", "bob@orbit.test", "Bob", "http://evil.local");

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void secondRequestDoesNotDuplicateUser() throws Exception {
        String token = token("user_test_3", "charlie@orbit.test", "Charlie", "http://localhost:5173");

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        Long count = jdbc.sql("SELECT COUNT(*) FROM users WHERE id = :id")
                .param("id", "user_test_3")
                .query(Long.class)
                .single();
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void changingClaimsUpdatesUser() throws Exception {
        String firstToken = token("user_test_4", "dana@orbit.test", "Dana", "http://localhost:5173");
        String secondToken = token("user_test_4", "dana.new@orbit.test", "Dana New", "http://localhost:5173");

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + firstToken))
                .andExpect(status().isOk());
        assertUser("user_test_4", "dana@orbit.test", "Dana");

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + secondToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("dana.new@orbit.test"));
        assertUser("user_test_4", "dana.new@orbit.test", "Dana New");
    }

    private String token(String subject, String email, String name, String azp) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("http://localhost")
                .issuedAt(now)
                .expiresAt(now.plus(5, ChronoUnit.MINUTES))
                .subject(subject)
                .claim("email", email)
                .claim("name", name)
                .claim("azp", azp)
                .build();

        JwsHeader header = JwsHeader.with(org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private void assertUser(String id, String email, String displayName) {
        Map<String, String> row = jdbc.sql("SELECT email, display_name FROM users WHERE id = :id")
                .param("id", id)
                .query((rs, rowNum) -> Map.of("email", rs.getString("email"), "display_name", rs.getString("display_name")))
                .single();
        assertThat(row.get("email")).isEqualTo(email);
        assertThat(row.get("display_name")).isEqualTo(displayName);
    }

}
