package com.orbit.app.search;

import java.util.List;

import com.orbit.testsupport.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** Regression coverage for the database-resident RRF retrieval and scope filter. */
@SpringBootTest(properties = {
        "HF_TOKEN=test-offline-token",
        "GEMINI_API_KEY=test-offline-key"
})
class HybridSearchDatabaseIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanSearchData() {
        jdbc.execute("TRUNCATE TABLE chunks, sources, entity_identifiers, entity_tags, "
                + "hub_memberships, entities RESTART IDENTITY CASCADE");
    }

    @Test
    void hybridSearchHonoursActiveSourcesAndRegionScopeAcrossLexicalAndVectorRetrieval() {
        long baden = id("SELECT id FROM regions WHERE slug = 'region-baden'");
        long switzerland = id("SELECT id FROM regions WHERE slug = 'ch'");

        long inScope = entity("In scope firmware specialist", baden, "active");
        long outsideScope = entity("Swiss firmware specialist", switzerland, "active");
        long inactiveSource = entity("Hidden firmware specialist", baden, "active");
        sourceWithChunk(inScope, true, "embedded firmware security specialist", 1.0f);
        sourceWithChunk(outsideScope, true, "embedded firmware security specialist", 0.9f);
        sourceWithChunk(inactiveSource, false, "embedded firmware security specialist", 0.8f);

        List<Long> regional = resultIds("firmware", vector(1.0f), "{" + baden + "}");
        assertThat(regional).containsExactly(inScope);

        List<Long> nationwide = resultIds("firmware", vector(1.0f), "{}");
        assertThat(nationwide).containsExactly(inScope, outsideScope);
        assertThat(nationwide).doesNotContain(inactiveSource);
    }

    private long entity(String name, long regionId, String status) {
        return jdbc.queryForObject("""
                INSERT INTO entities (entity_type, display_name, status, home_region_id)
                VALUES ('person', ?, ?, ?) RETURNING id
                """, Long.class, name, status, regionId);
    }

    private void sourceWithChunk(long entityId, boolean active, String content, float signal) {
        long sourceId = jdbc.queryForObject("""
                INSERT INTO sources (entity_id, source_type, source_url, label, provenance, active)
                VALUES (?, 'manual_text', ?, 'Test evidence', 'user_supplied', ?) RETURNING id
                """, Long.class, entityId, "test://source/" + entityId, active);
        jdbc.update("""
                INSERT INTO chunks (source_id, entity_id, chunk_index, content, embedding)
                VALUES (?, ?, 0, ?, ?::vector)
                """, sourceId, entityId, content, vector(signal));
    }

    private List<Long> resultIds(String query, String embedding, String regionIds) {
        return jdbc.queryForList("""
                SELECT entity_id FROM hybrid_search(?, ?::vector, '{}'::bigint[], ?::bigint[], 10, 1.0, 1.0, 50)
                """, Long.class, query, embedding, regionIds);
    }

    private long id(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    private static String vector(float firstValue) {
        StringBuilder value = new StringBuilder("[").append(firstValue);
        for (int i = 1; i < 1024; i++) value.append(",0");
        return value.append(']').toString();
    }
}
