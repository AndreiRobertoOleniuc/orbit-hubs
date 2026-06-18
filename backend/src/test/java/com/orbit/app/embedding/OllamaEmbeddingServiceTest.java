package com.orbit.app.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;

/**
 * Unit test for the wrapper contract: e5 prefixing and dimension validation. Mocks the
 * Spring AI {@link EmbeddingModel} so it runs without Ollama or a Spring context.
 */
@ExtendWith(MockitoExtension.class)
class OllamaEmbeddingServiceTest {

    private static final EmbeddingProperties PROPS = new EmbeddingProperties("http://localhost:11434", "test-model", 1024, "query: ", "passage: ");

    @Mock
    private EmbeddingModel embeddingModel;

    @Test
    void prefixesQueriesAndPassagesPerKind() {
        when(embeddingModel.embed(anyString())).thenReturn(new float[1024]);
        var service = new OllamaEmbeddingService(embeddingModel, PROPS);

        service.embed("firmware security", EmbeddingKind.QUERY);
        service.embed("ten years of embedded firmware work", EmbeddingKind.PASSAGE);

        var captor = ArgumentCaptor.forClass(String.class);
        verify(embeddingModel, times(2)).embed(captor.capture());
        assertThat(captor.getAllValues()).containsExactly("query: firmware security", "passage: ten years of embedded firmware work");
    }

    @Test
    void returnsConfiguredDimension() {
        when(embeddingModel.embed("query: x")).thenReturn(new float[1024]);
        var service = new OllamaEmbeddingService(embeddingModel, PROPS);

        assertThat(service.embed("x", EmbeddingKind.QUERY)).hasSize(1024);
    }

    @Test
    void rejectsWrongDimensionFromProvider() {
        when(embeddingModel.embed("query: x")).thenReturn(new float[512]);
        var service = new OllamaEmbeddingService(embeddingModel, PROPS);

        assertThatThrownBy(() -> service.embed("x", EmbeddingKind.QUERY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("512")
                .hasMessageContaining("1024");
    }

    @Test
    void rejectsBlankText() {
        var service = new OllamaEmbeddingService(embeddingModel, PROPS);

        assertThatThrownBy(() -> service.embed("  ", EmbeddingKind.QUERY))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
