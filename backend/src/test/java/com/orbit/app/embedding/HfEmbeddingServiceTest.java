package com.orbit.app.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.orbit.app.inference.HfInferenceClient;
import com.orbit.app.inference.HfInferenceProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class HfEmbeddingServiceTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();
    private final HfInferenceClient client = mock(HfInferenceClient.class);
    private final HfInferenceProperties hfProps = new HfInferenceProperties(
            "hf_test", "https://example.test/models", "embed-model", "rerank-model",
            Duration.ofMinutes(2), Duration.ofSeconds(5), Duration.ofSeconds(30));
    private final EmbeddingProperties embeddingProps = new EmbeddingProperties(
            "http://localhost:11434", "local", 3, "query: ", "passage: ");

    @Test
    void prefixesAndParsesFlatVector() throws Exception {
        when(client.post(eq("embed-model"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(objectMapper.readTree("[0.1,0.2,0.3]"));
        HfEmbeddingService service = new HfEmbeddingService(client, hfProps, embeddingProps, objectMapper);

        assertThat(service.embed("security", EmbeddingKind.QUERY)).containsExactly(0.1f, 0.2f, 0.3f);

        ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
        verify(client).post(eq("embed-model"), body.capture());
        assertThat(body.getValue().get("inputs").asText()).isEqualTo("query: security");
    }

    @Test
    void embedAllSendsOneRequestPerBatchAndKeepsInputOrder() throws Exception {
        when(client.post(eq("embed-model"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(objectMapper.readTree("[[0.1,0.2,0.3],[0.4,0.5,0.6]]"));
        HfEmbeddingService service = new HfEmbeddingService(client, hfProps, embeddingProps, objectMapper);

        var vectors = service.embedAll(java.util.List.of("first", "second"), EmbeddingKind.PASSAGE);

        assertThat(vectors).hasSize(2);
        assertThat(vectors.get(1)).containsExactly(0.4f, 0.5f, 0.6f);
        ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
        verify(client).post(eq("embed-model"), body.capture());
        assertThat(body.getValue().get("inputs").get(0).asText()).isEqualTo("passage: first");
        assertThat(body.getValue().get("inputs").get(1).asText()).isEqualTo("passage: second");

        when(client.post(eq("embed-model"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(objectMapper.readTree("[[0.1,0.2,0.3]]"));
        assertThatThrownBy(() -> service.embedAll(java.util.List.of("a", "b"), EmbeddingKind.PASSAGE))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("1 vectors for 2 inputs");
    }

    @Test
    void acceptsSingletonNestedVector() throws Exception {
        when(client.post(eq("embed-model"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(objectMapper.readTree("[[0.1,0.2,0.3]]"));
        HfEmbeddingService service = new HfEmbeddingService(client, hfProps, embeddingProps, objectMapper);

        assertThat(service.embed("profile", EmbeddingKind.PASSAGE)).hasSize(3);
    }

    @Test
    void rejectsWrongDimensionsAndNonFiniteValues() throws Exception {
        HfEmbeddingService service = new HfEmbeddingService(client, hfProps, embeddingProps, objectMapper);
        when(client.post(eq("embed-model"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(objectMapper.readTree("[0.1,0.2]"));
        assertThatThrownBy(() -> service.embed("x", EmbeddingKind.QUERY))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("2 dimensions");

        when(client.post(eq("embed-model"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(objectMapper.readTree("[0.1,\"bad\",0.3]"));
        assertThatThrownBy(() -> service.embed("x", EmbeddingKind.QUERY))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("non-numeric");
    }
}
