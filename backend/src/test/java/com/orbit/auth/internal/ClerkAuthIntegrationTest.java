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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
        // Order matters: requests cascade request_tags/request_distributions;
        // entities cascade sources/entity_tags/contact_requests; users last (FKs).
        jdbc.sql("DELETE FROM requests WHERE created_by LIKE 'user_test_%'").update();
        jdbc.sql("DELETE FROM entities WHERE owner_id LIKE 'user_test_%'").update();
        jdbc.sql("DELETE FROM entities WHERE display_name LIKE 'IT-Test %'").update();
        jdbc.sql("DELETE FROM users WHERE id LIKE 'user_test_%'").update();
    }

    @Test
    void publicRouteIsReachableWithoutToken() throws Exception {
        // /api/v1/board is a whitelisted public route — 200 (not 401) without a token
        mockMvc.perform(get("/api/v1/board"))
                .andExpect(status().isOk());
    }

    @Test
    void boardShowsDistributedRequestsAndHidesClosedOnes() throws Exception {
        // Regression: distribution runs on create, so a fresh request is
        // 'distributed', never 'open'. The board must still list it (every
        // live request shows until closed, §8.1) — filtering on 'open' would
        // leave the board permanently empty.
        String token = token("user_test_board", "board@orbit.test", "Boardy", "http://localhost:5173");

        String created = mockMvc.perform(post("/api/v1/requests")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"IT-Board-Test\", \"queryText\": \"Board sichtbar?\", \"scope\": {\"kind\": \"all\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("distributed"))
                .andReturn().getResponse().getContentAsString();
        long requestId = ((Number) com.jayway.jsonpath.JsonPath.read(created, "$.id")).longValue();

        // The distributed request is on the public board.
        mockMvc.perform(get("/api/v1/board"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.title=='IT-Board-Test')].status").value("distributed"));

        // Closing it removes it from the board.
        mockMvc.perform(put("/api/v1/requests/" + requestId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"closed\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/board"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.title=='IT-Board-Test')]").isEmpty());
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
                // Creation auto-distributes (§8.2) — no matching profiles here, so count 0.
                .andExpect(jsonPath("$.status").value("distributed"))
                .andExpect(jsonPath("$.distributionCount").value(0))
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

    // ─── Distribution + contact handoff + inbox (§8.2, §11) ───

    @Test
    void distributionReachesTaggedEntityAndShowsUpInInbox() throws Exception {
        String tokenA = token("user_test_da", "da@orbit.test", "Alice", "http://localhost:5173");
        String tokenB = token("user_test_db", "db@orbit.test", "Bea", "http://localhost:5173");
        provision(tokenA);
        provision(tokenB);

        long tagId = insertTag("embedded", "Embedded");
        long entityB = insertEntity("user_test_db", "IT-Test Bea", "self_employed");
        jdbc.sql("INSERT INTO entity_tags (entity_id, tag_id, origin) VALUES (:e, :t, 'manual')")
                .param("e", entityB).param("t", tagId).update();

        String created = mockMvc.perform(post("/api/v1/requests")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Firmware-Audit", "queryText": "Embedded Audit",
                                 "scope": {"kind": "all"}, "urgency": "flexible",
                                 "tags": ["Embedded"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("distributed"))
                .andExpect(jsonPath("$.distributionCount").value(1))
                .andExpect(jsonPath("$.wideningLevel").value("all"))
                // The creator sees WHO was notified + why.
                .andExpect(jsonPath("$.distributions[0].name").value("IT-Test Bea"))
                .andExpect(jsonPath("$.distributions[0].entityId").value((int) entityB))
                .andExpect(jsonPath("$.distributions[0].reason").value("profile_match"))
                .andExpect(jsonPath("$.distributions[0].matchedTags[0]").value("Embedded"))
                .andReturn().getResponse().getContentAsString();
        long requestId = ((Number) com.jayway.jsonpath.JsonPath.read(created, "$.id")).longValue();

        // The notified profile sees the request in their Postfach.
        mockMvc.perform(get("/api/v1/inbox").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.distributions[0].title").value("Firmware-Audit"))
                .andExpect(jsonPath("$.distributions[0].reason").value("profile_match"))
                .andExpect(jsonPath("$.distributions[0].matchedTags[0]").value("Embedded"));

        // A non-owner (public read) never sees the notified-profiles list.
        mockMvc.perform(get("/api/v1/requests/" + requestId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mine").value(false))
                .andExpect(jsonPath("$.distributions").doesNotExist())
                .andExpect(jsonPath("$.distributionCount").doesNotExist());
    }

    @Test
    void contactGatingReportsMissingChecksAndBlocksSend() throws Exception {
        String token = token("user_test_nogate", "nogate@orbit.test", "Noa", "http://localhost:5173");
        provision(token);

        mockMvc.perform(get("/api/v1/contacts/gating").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.missing").isArray());

        mockMvc.perform(post("/api/v1/contacts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toEntityId\": 1, \"message\": \"Hallo\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void contactHappyPathTransitionsRequestAndLandsInRecipientInbox() throws Exception {
        String tokenA = token("user_test_ca", "ca@orbit.test", "Alice", "http://localhost:5173");
        String tokenB = token("user_test_cb", "cb@orbit.test", "Bea", "http://localhost:5173");
        provision(tokenA);
        provision(tokenB);

        long entityA = insertEntity("user_test_ca", "IT-Test Alice", "company");
        long entityB = insertEntity("user_test_cb", "IT-Test Bea", "self_employed");
        insertSource(entityB, "manual_text");

        String created = mockMvc.perform(post("/api/v1/requests")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Kontakt-Test\", \"queryText\": \"Suche Hilfe\", \"scope\": {\"kind\": \"all\"}}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long requestId = ((Number) com.jayway.jsonpath.JsonPath.read(created, "$.id")).longValue();

        // B is gated OK (entity + email + manual_text source; affiliation not required).
        mockMvc.perform(get("/api/v1/contacts/gating").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));

        String contactJson = mockMvc.perform(post("/api/v1/contacts")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toEntityId\": " + entityA + ", \"requestId\": " + requestId
                                + ", \"message\": \"Hätte Kapazität ab August.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromEntityId").value(entityB))
                .andExpect(jsonPath("$.toEntityId").value(entityA))
                .andReturn().getResponse().getContentAsString();
        long contactId = ((Number) com.jayway.jsonpath.JsonPath.read(contactJson, "$.id")).longValue();

        // First contact flips the request to 'contacted' (§8.1).
        mockMvc.perform(get("/api/v1/requests/" + requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("contacted"));

        // Lands in A's Postfach as pending — B's email is still hidden (mutual opt-in).
        mockMvc.perform(get("/api/v1/inbox").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received[0].fromName").value("IT-Test Bea"))
                .andExpect(jsonPath("$.received[0].status").value("pending"))
                .andExpect(jsonPath("$.received[0].fromEmail").doesNotExist())
                .andExpect(jsonPath("$.received[0].senderContext.searchedText").value("Suche Hilfe"))
                .andExpect(jsonPath("$.received[0].message").value("Hätte Kapazität ab August."));

        // B's Postfach shows the sent request as pending, recipient email hidden.
        mockMvc.perform(get("/api/v1/inbox").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sent[0].toName").value("IT-Test Alice"))
                .andExpect(jsonPath("$.sent[0].status").value("pending"))
                .andExpect(jsonPath("$.sent[0].toEmail").doesNotExist());

        // A (the recipient) accepts → mutual email reveal.
        mockMvc.perform(post("/api/v1/contacts/" + contactId + "/respond")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accept\": true, \"message\": \"Ja, gerne — melde dich!\"}"))
                .andExpect(status().isOk());

        // A now sees B's email; B now sees A's email + A's reply.
        mockMvc.perform(get("/api/v1/inbox").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received[0].status").value("accepted"))
                .andExpect(jsonPath("$.received[0].fromEmail").value("cb@orbit.test"));

        mockMvc.perform(get("/api/v1/inbox").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sent[0].status").value("accepted"))
                .andExpect(jsonPath("$.sent[0].toEmail").value("ca@orbit.test"))
                .andExpect(jsonPath("$.sent[0].replyMessage").value("Ja, gerne — melde dich!"));
    }

    @Test
    void declinedContactRevealsNoEmails() throws Exception {
        String tokenA = token("user_test_dca", "dca@orbit.test", "Alice", "http://localhost:5173");
        String tokenB = token("user_test_dcb", "dcb@orbit.test", "Bea", "http://localhost:5173");
        provision(tokenA);
        provision(tokenB);

        long entityA = insertEntity("user_test_dca", "IT-Test Alice", "company");
        long entityB = insertEntity("user_test_dcb", "IT-Test Bea", "self_employed");
        insertSource(entityB, "manual_text");

        String contactJson = mockMvc.perform(post("/api/v1/contacts")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toEntityId\": " + entityA + ", \"message\": \"Kurze Frage.\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long contactId = ((Number) com.jayway.jsonpath.JsonPath.read(contactJson, "$.id")).longValue();

        // A declines.
        mockMvc.perform(post("/api/v1/contacts/" + contactId + "/respond")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accept\": false}"))
                .andExpect(status().isOk());

        // Neither side gets an email; both see 'declined'.
        mockMvc.perform(get("/api/v1/inbox").header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.received[0].status").value("declined"))
                .andExpect(jsonPath("$.received[0].fromEmail").doesNotExist());
        mockMvc.perform(get("/api/v1/inbox").header("Authorization", "Bearer " + tokenB))
                .andExpect(jsonPath("$.sent[0].status").value("declined"))
                .andExpect(jsonPath("$.sent[0].toEmail").doesNotExist());
    }

    @Test
    void onlyRecipientCanRespondToContact() throws Exception {
        String tokenA = token("user_test_rra", "rra@orbit.test", "Alice", "http://localhost:5173");
        String tokenB = token("user_test_rrb", "rrb@orbit.test", "Bea", "http://localhost:5173");
        provision(tokenA);
        provision(tokenB);

        long entityA = insertEntity("user_test_rra", "IT-Test Alice", "company");
        long entityB = insertEntity("user_test_rrb", "IT-Test Bea", "self_employed");
        insertSource(entityB, "manual_text");

        String contactJson = mockMvc.perform(post("/api/v1/contacts")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toEntityId\": " + entityA + ", \"message\": \"Hallo.\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long contactId = ((Number) com.jayway.jsonpath.JsonPath.read(contactJson, "$.id")).longValue();

        // B (the sender, not the recipient) cannot respond → 403.
        mockMvc.perform(post("/api/v1/contacts/" + contactId + "/respond")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accept\": true, \"message\": \"nope\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void contactToUnclaimedEntityIsRejected() throws Exception {
        String tokenB = token("user_test_ub", "ub@orbit.test", "Bea", "http://localhost:5173");
        provision(tokenB);
        long entityB = insertEntity("user_test_ub", "IT-Test Bea", "self_employed");
        insertSource(entityB, "manual_text");
        long unclaimed = insertEntity(null, "IT-Test Harvested", null);

        mockMvc.perform(post("/api/v1/contacts")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toEntityId\": " + unclaimed + ", \"message\": \"Hallo\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void inboxRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/inbox"))
                .andExpect(status().isUnauthorized());
    }

    // ─── Fixtures ───

    /** JIT-provision the user row (entities.owner_id has an FK to users.id). */
    private void provision(String token) throws Exception {
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private long insertTag(String slug, String label) {
        return jdbc.sql("""
                        INSERT INTO tags (slug, label) VALUES (:slug, :label)
                        ON CONFLICT (slug) DO UPDATE SET label = EXCLUDED.label
                        RETURNING id
                        """)
                .param("slug", slug).param("label", label)
                .query(Long.class).single();
    }

    private long insertEntity(String ownerId, String displayName, String affiliationKind) {
        return jdbc.sql("""
                        INSERT INTO entities (entity_type, display_name, headline, owner_id, affiliation_kind)
                        VALUES ('person', :name, 'Engineer', :owner, :affiliation)
                        RETURNING id
                        """)
                .param("name", displayName).param("owner", ownerId).param("affiliation", affiliationKind)
                .query(Long.class).single();
    }

    private void insertSource(long entityId, String sourceType) {
        jdbc.sql("""
                        INSERT INTO sources (entity_id, source_type, source_url, label, provenance)
                        VALUES (:entity, :type, :url, 'Eigene Beschreibung', 'self')
                        ON CONFLICT (source_url) DO NOTHING
                        """)
                .param("entity", entityId).param("type", sourceType)
                .param("url", "test://" + sourceType + "/" + entityId)
                .update();
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
