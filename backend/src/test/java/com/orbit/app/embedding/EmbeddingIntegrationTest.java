package com.orbit.app.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * End-to-end embedding check against a real Ollama. Verifies the wired bean produces a
 * {@code vector(1024)} from the configured multilingual-e5-large model.
 *
 * <p>Guarded by {@code OLLAMA_E2E=true} because it needs a running Ollama with the model
 * pulled (not part of compose.yaml). Run with:
 * {@code OLLAMA_E2E=true ./mvnw test -Dtest=EmbeddingIntegrationTest}.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "OLLAMA_E2E", matches = "true")
class EmbeddingIntegrationTest {

    @Autowired
    private EmbeddingService embeddingService;

    @Test
    void embedsQueryToConfiguredDimension() {
        float[] vector = embeddingService.embed("embedded firmware security review", EmbeddingKind.QUERY);

        assertThat(vector).hasSize(1024);
    }

    @Test
    void embedsPassageToConfiguredDimension() {
        float[] vector = embeddingService.embed(
                "Senior security engineer with ten years of embedded firmware experience.",
                EmbeddingKind.PASSAGE);

        assertThat(vector).hasSize(1024);
    }
}
