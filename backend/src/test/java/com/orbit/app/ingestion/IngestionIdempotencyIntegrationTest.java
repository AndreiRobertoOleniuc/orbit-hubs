package com.orbit.app.ingestion;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.orbit.app.embedding.EmbeddingKind;
import com.orbit.app.embedding.EmbeddingService;
import com.orbit.app.ingestion.dto.EntityProfile;
import com.orbit.app.ingestion.dto.RawContent;
import com.orbit.app.ingestion.dto.SourceRef;
import com.orbit.domain.repository.SourceRepository;
import com.orbit.testsupport.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Exercises the persisted source hash contract instead of only mocking repositories. */
@SpringBootTest(properties = {
        "HF_TOKEN=test-offline-token",
        "GEMINI_API_KEY=test-offline-key"
})
class IngestionIdempotencyIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private IngestionOrchestrator orchestrator;

    @Autowired
    private SourceRepository sources;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private SourceAdapterRegistry adapterRegistry;

    @MockitoBean
    private EmbeddingService embeddingService;

    private final SourceAdapter adapter = org.mockito.Mockito.mock(SourceAdapter.class);

    private final SourceRef ref = new SourceRef(
            "test://idempotency/ada", "test_source", "user_supplied", "self_declared", Map.of(), "user-test");

    @BeforeEach
    void setUp() throws Exception {
        jdbc.execute("TRUNCATE TABLE chunks, sources, entity_identifiers, entity_tags, entities RESTART IDENTITY CASCADE");
        jdbc.update("INSERT INTO users (id, email, display_name) VALUES ('user-test', 'user@orbit.test', 'Test User') "
                + "ON CONFLICT (id) DO NOTHING");
        when(adapterRegistry.getAdapter("test_source")).thenReturn(adapter);
        when(adapter.buildLabel(any())).thenReturn("Idempotency evidence");
        when(adapter.extractExternalIds(any())).thenReturn(List.of());
        when(adapter.deriveProfile(any())).thenReturn(new EntityProfile(
                "person", "Ada Test", "Search specialist", null, null, null, Map.of()));
        when(adapter.extractHighSignalCore(any())).thenAnswer(invocation ->
                invocation.getArgument(0, RawContent.class).text());
        when(embeddingService.embed(any(), eq(EmbeddingKind.PASSAGE))).thenReturn(vector());
    }

    @Test
    void unchangedSourceSkipsEmbeddingWhileChangedContentReplacesItsChunks() throws Exception {
        RawContent first = new RawContent("First embedded firmware evidence.", Map.of(), Instant.parse("2026-01-01T00:00:00Z"));
        RawContent changed = new RawContent("Changed cybersecurity evidence.", Map.of(), Instant.parse("2026-01-02T00:00:00Z"));
        when(adapter.fetch(ref)).thenReturn(first, first, changed);

        var initial = orchestrator.ingest(ref);
        var unchanged = orchestrator.ingest(ref);
        var updated = orchestrator.ingest(ref);

        assertThat(initial.status().name()).isEqualTo("INGESTED");
        assertThat(unchanged.status().name()).isEqualTo("SKIPPED");
        assertThat(updated.status().name()).isEqualTo("INGESTED");
        assertThat(sources.count()).isEqualTo(1);
        String persistedChunk = jdbc.queryForObject("SELECT content FROM chunks", String.class);
        assertThat(persistedChunk).isEqualTo("Changed cybersecurity evidence.");
        verify(embeddingService, times(2)).embed(any(), eq(EmbeddingKind.PASSAGE));
    }

    private static float[] vector() {
        return new float[1024];
    }
}
