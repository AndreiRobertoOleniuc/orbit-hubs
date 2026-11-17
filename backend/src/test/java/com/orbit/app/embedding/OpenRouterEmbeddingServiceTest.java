package com.orbit.app.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.orbit.app.inference.OpenRouterClient;
import com.orbit.app.inference.OpenRouterProperties;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class OpenRouterEmbeddingServiceTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();
    private final OpenRouterClient client = mock(OpenRouterClient.class);

    @Test
    void aPrefixModelGetsItsPrefixAndTheStoredVectorSizeButNoInputType() {
        answer("{\"data\":[{\"index\":0,\"embedding\":[0.1,0.2,0.3]}]}");
        OpenRouterEmbeddingService service = service("intfloat/multilingual-e5-large", "query: ", "passage: ", "", "");

        assertThat(service.embed("security", EmbeddingKind.QUERY)).containsExactly(0.1f, 0.2f, 0.3f);

        ObjectNode request = sentRequest();
        assertThat(request.get("model").asString()).isEqualTo("intfloat/multilingual-e5-large");
        assertThat(request.get("input").get(0).asString()).isEqualTo("query: security");
        assertThat(request.get("dimensions").asInt()).isEqualTo(3);
        assertThat(request.has("input_type")).isFalse();
    }

    @Test
    void anInputTypeModelIsToldWhetherTheTextIsAQueryOrAPassage() {
        answer("{\"data\":[{\"index\":0,\"embedding\":[0.1,0.2,0.3]}]}");
        OpenRouterEmbeddingService service = service("some/model", "", "", "search_query", "search_document");

        service.embed("security", EmbeddingKind.QUERY);
        service.embed("A passage about security.", EmbeddingKind.PASSAGE);

        ArgumentCaptor<ObjectNode> requests = ArgumentCaptor.forClass(ObjectNode.class);
        verify(client, times(2)).post(eq("embeddings"), requests.capture());
        assertThat(requests.getAllValues().get(0).get("input_type").asString()).isEqualTo("search_query");
        assertThat(requests.getAllValues().get(0).get("input").get(0).asString()).isEqualTo("security");
        assertThat(requests.getAllValues().get(1).get("input_type").asString()).isEqualTo("search_document");
    }

    @Test
    void vectorsComeBackInInputOrderWhateverOrderTheResponseListsThem() {
        answer("{\"data\":[{\"index\":1,\"embedding\":[0.4,0.5,0.6]},{\"index\":0,\"embedding\":[0.1,0.2,0.3]}]}");
        OpenRouterEmbeddingService service = service("some/model", "", "passage: ", "", "");

        List<float[]> vectors = service.embedAll(List.of("first", "second"), EmbeddingKind.PASSAGE);

        assertThat(vectors.get(0)).containsExactly(0.1f, 0.2f, 0.3f);
        assertThat(vectors.get(1)).containsExactly(0.4f, 0.5f, 0.6f);
        assertThat(sentRequest().get("input").get(1).asString()).isEqualTo("passage: second");
    }

    @Test
    void manyTextsAreSentInBatches() {
        when(client.post(eq("embeddings"), any())).thenAnswer(invocation -> {
            int inputs = invocation.<ObjectNode>getArgument(1).get("input").size();
            ObjectNode response = objectMapper.createObjectNode();
            IntStream.range(0, inputs).forEach(i -> response.withArray("data").addObject()
                    .put("index", i).putArray("embedding").add(0.1).add(0.2).add(0.3));
            return response;
        });
        OpenRouterEmbeddingService service = service("some/model", "", "", "", "");
        List<String> texts = IntStream.range(0, OpenRouterEmbeddingService.BATCH_SIZE + 1)
                .mapToObj(i -> "text " + i).toList();

        assertThat(service.embedAll(texts, EmbeddingKind.PASSAGE)).hasSize(texts.size());

        verify(client, times(2)).post(eq("embeddings"), any());
    }

    @Test
    void aWrongVectorSizeOrCountIsRejected() {
        OpenRouterEmbeddingService service = service("some/model", "", "", "", "");

        answer("{\"data\":[{\"index\":0,\"embedding\":[0.1,0.2]}]}");
        assertThatThrownBy(() -> service.embed("x", EmbeddingKind.QUERY))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("returned 2 dimensions, expected 3");

        answer("{\"data\":[{\"index\":0,\"embedding\":[0.1,0.2,0.3]}]}");
        assertThatThrownBy(() -> service.embedAll(List.of("a", "b"), EmbeddingKind.PASSAGE))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("1 vectors for 2 inputs");

        answer("{\"data\":[{\"index\":0,\"embedding\":[0.1,0.2,0.3]},{\"index\":0,\"embedding\":[0.1,0.2,0.3]}]}");
        assertThatThrownBy(() -> service.embedAll(List.of("a", "b"), EmbeddingKind.PASSAGE))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("duplicate index");
    }

    @Test
    void vectorsAreStoredUnderTheConfiguredModelId() {
        assertThat(service("mongodb/voyage-4-lite", "", "", "", "").modelName()).isEqualTo("mongodb/voyage-4-lite");
    }

    private void answer(String json) {
        when(client.post(eq("embeddings"), any())).thenAnswer(invocation -> objectMapper.readTree(json));
    }

    private ObjectNode sentRequest() {
        ArgumentCaptor<ObjectNode> request = ArgumentCaptor.forClass(ObjectNode.class);
        verify(client).post(eq("embeddings"), request.capture());
        return request.getValue();
    }

    private OpenRouterEmbeddingService service(String model, String queryPrefix, String passagePrefix,
            String queryInputType, String passageInputType) {
        OpenRouterProperties props = new OpenRouterProperties("sk-test", "https://openrouter.test/api/v1", "deny",
                Duration.ofMinutes(2), Duration.ofSeconds(5), Duration.ofSeconds(30),
                new OpenRouterProperties.Chat("chat/model", List.of(), 0.3, 500, Duration.ofSeconds(20)),
                new OpenRouterProperties.Embedding(model, 3, queryInputType, passageInputType, queryPrefix, passagePrefix),
                new OpenRouterProperties.Reranker("rerank/model"));
        return new OpenRouterEmbeddingService(client, props, objectMapper);
    }
}
