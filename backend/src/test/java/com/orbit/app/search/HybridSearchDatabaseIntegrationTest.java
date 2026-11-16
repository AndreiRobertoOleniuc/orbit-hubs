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
@SpringBootTest
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

    @Test
    void aProlificEntityTakesOneCandidateSlotNotAllOfThem() {
        long baden = id("SELECT id FROM regions WHERE slug = 'region-baden'");
        long prolific = entity("Prolific researcher", baden, "active");
        long single = entity("Single-source company", baden, "active");
        for (int i = 0; i < 40; i++) {
            sourceWithChunk(prolific, true, "robotics paper " + i, 1.0f - i * 0.001f, "work-" + i);
        }
        sourceWithChunk(single, true, "robotics company", 0.5f);

        // Two candidate slots: the V15 version filled both with the prolific
        // entity's chunks before grouping, and dropped the company.
        List<Long> candidates = search(null, vector(1.0f), "{}", 2, 0.0d, 1.0d);

        assertThat(candidates).containsExactly(prolific, single);
    }

    @Test
    void theKeywordLegMatchesAnyQueryTermAndRanksRareTermsFirst() {
        long baden = id("SELECT id FROM regions WHERE slug = 'region-baden'");
        long rare = entity("Wastewater specialist", baden, "active");
        long common = entity("Project manager", baden, "active");
        long unrelated = entity("Unrelated", baden, "active");
        sourceWithChunk(rare, true, "Removal of micropollutants from wastewater", 0.1f);
        for (int i = 0; i < 5; i++) {
            sourceWithChunk(common, true, "A project about project management " + i, 0.1f, "p-" + i);
        }
        sourceWithChunk(unrelated, true, "Quantum sensing with diamonds", 0.1f);

        // No chunk contains every term: AND semantics found nothing here.
        List<Long> candidates = search("wastewater treatment project", vector(0.1f), "{}", 10, 1.0d, 0.0d);

        assertThat(candidates).containsExactly(rare, common);
    }

    @Test
    void theProfileDocumentMakesAnEntityFindableWithoutAMatchingChunk() {
        long baden = id("SELECT id FROM regions WHERE slug = 'region-baden'");
        long profileOnly = entity("Grete Beispiel", baden, "active");
        long other = entity("Other", baden, "active");
        sourceWithChunk(profileOnly, true, "unrelated chunk", 0.1f);
        sourceWithChunk(other, true, "unrelated chunk", 0.2f);
        jdbc.update("""
                INSERT INTO profile_documents (entity_id, content, embedding, embedding_model)
                VALUES (?, 'Grete Beispiel\nSoftwareentwicklung · Cloud', ?::vector, 'multilingual-e5-large')
                """, profileOnly, vector(0.9f));

        assertThat(search("Beispiel", vector(0.0f), "{}", 10, 1.0d, 0.0d)).containsExactly(profileOnly);
        assertThat(search(null, vector(1.0f), "{}", 1, 0.0d, 1.0d)).containsExactly(profileOnly);
    }

    @Test
    void traceRankingCollapsesChunksToOneRowPerEntityWithinScopeAndActiveSources() {
        long baden = id("SELECT id FROM regions WHERE slug = 'region-baden'");
        long switzerland = id("SELECT id FROM regions WHERE slug = 'ch'");

        long prolific = entity("Prolific researcher", baden, "active");
        long single = entity("Single-source specialist", baden, "active");
        long outsideScope = entity("Swiss specialist", switzerland, "active");
        long inactiveSource = entity("Hidden specialist", baden, "active");
        long draft = entity("Draft specialist", baden, "draft");
        sourceWithChunk(prolific, true, "robotics", 1.0f, "a");
        sourceWithChunk(prolific, true, "robotics", 0.99f, "b");
        sourceWithChunk(prolific, true, "robotics", 0.98f, "c");
        sourceWithChunk(single, true, "robotics", 0.5f, "a");
        sourceWithChunk(outsideScope, true, "robotics", 0.97f, "a");
        sourceWithChunk(inactiveSource, false, "robotics", 0.96f, "a");
        sourceWithChunk(draft, true, "robotics", 0.95f, "a");

        String regionIds = "{" + baden + "}";
        List<java.util.Map<String, Object>> rows = jdbc.queryForList(
                SearchService.SEMANTIC_ENTITY_RANKING_SQL,
                vector(1.0f), vector(1.0f), "{}", regionIds, regionIds, "{}", 50);

        // One row per entity; the single-source entity sits right behind the
        // prolific one's three chunks instead of being pushed out by them.
        assertThat(rows).extracting(row -> ((Number) row.get("entity_id")).longValue())
                .containsExactly(prolific, single);
        assertThat(rows).extracting(row -> ((Number) row.get("best_rank")).intValue())
                .containsExactly(1, 4);
        assertThat(((Number) rows.get(1).get("best_similarity")).doubleValue())
                .isCloseTo(0.5d, org.assertj.core.data.Offset.offset(0.0001d));
    }

    private long entity(String name, long regionId, String status) {
        return jdbc.queryForObject("""
                INSERT INTO entities (entity_type, display_name, status, home_region_id)
                VALUES ('person', ?, ?, ?) RETURNING id
                """, Long.class, name, status, regionId);
    }

    private void sourceWithChunk(long entityId, boolean active, String content, float signal) {
        sourceWithChunk(entityId, active, content, signal, "only");
    }

    /** {@code key} distinguishes several sources of one entity (source_url is unique). */
    private void sourceWithChunk(long entityId, boolean active, String content, float signal, String key) {
        long sourceId = jdbc.queryForObject("""
                INSERT INTO sources (entity_id, source_type, source_url, label, provenance, active)
                VALUES (?, 'manual_text', ?, 'Test evidence', 'user_supplied', ?) RETURNING id
                """, Long.class, entityId, "test://source/" + entityId + "/" + key, active);
        jdbc.update("""
                INSERT INTO chunks (source_id, entity_id, chunk_index, content, embedding)
                VALUES (?, ?, 0, ?, ?::vector)
                """, sourceId, entityId, content, vector(signal));
    }

    private List<Long> resultIds(String query, String embedding, String regionIds) {
        return search(query, embedding, regionIds, 10, 1.0d, 1.0d);
    }

    /** Entity ids from the production retrieval query, original-language leg off. */
    private List<Long> search(String keywords, String embedding, String regionIds, int depth,
                              double lexicalWeight, double semanticWeight) {
        return jdbc.query(SearchService.HYBRID_SEARCH_SQL, (row, i) -> row.getLong("entity_id"),
                embedding, embedding, keywords, "{}", regionIds, depth, 2000,
                lexicalWeight, semanticWeight, 0.0d, 60);
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
