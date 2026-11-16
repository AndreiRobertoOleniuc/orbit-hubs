package com.orbit.app.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.orbit.app.inference.OpenRouterClient;
import com.orbit.app.inference.OpenRouterProperties;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class OpenRouterCrossEncoderClientTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();
    private final OpenRouterClient client = mock(OpenRouterClient.class);
    private final OpenRouterCrossEncoderClient reranker = new OpenRouterCrossEncoderClient(client,
            new OpenRouterProperties("sk-test", "https://openrouter.test/api/v1", "deny",
                    Duration.ofMinutes(2), Duration.ofSeconds(5), Duration.ofSeconds(30),
                    new OpenRouterProperties.Chat("chat/model", List.of(), 0.3, 500, Duration.ofSeconds(20)),
                    new OpenRouterProperties.Embedding("embed/model", "", ""),
                    new OpenRouterProperties.Reranker("cohere/rerank-test")),
            objectMapper);

    @Test
    void sendsOneRequestAndReturnsTheScoresInInputOrder() {
        // OpenRouter lists the passages best first.
        when(client.post(eq("rerank"), any())).thenReturn(objectMapper.readTree("""
                {"results":[{"index":1,"relevance_score":0.91},{"index":2,"relevance_score":0.4},
                            {"index":0,"relevance_score":0.02}]}"""));

        List<Double> scores = reranker.score("secure robotics", List.of("baking", "robot security", "robots"));

        assertThat(scores).containsExactly(0.02, 0.91, 0.4);
        ArgumentCaptor<ObjectNode> request = ArgumentCaptor.forClass(ObjectNode.class);
        verify(client).post(eq("rerank"), request.capture());
        assertThat(request.getValue().get("model").asString()).isEqualTo("cohere/rerank-test");
        assertThat(request.getValue().get("query").asString()).isEqualTo("secure robotics");
        assertThat(request.getValue().get("documents")).hasSize(3);
        assertThat(request.getValue().get("documents").get(1).asString()).isEqualTo("robot security");
    }

    @Test
    void rejectsAResponseThatDoesNotScoreEveryPassageExactlyOnce() {
        assertThatThrownBy(() -> reranker.parseScores(
                objectMapper.readTree("{\"results\":[{\"index\":0,\"relevance_score\":0.5}]}"), 2))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("returned 1 scores, expected 2");
        assertThatThrownBy(() -> reranker.parseScores(objectMapper.readTree(
                "{\"results\":[{\"index\":0,\"relevance_score\":0.5},{\"index\":0,\"relevance_score\":0.4}]}"), 2))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("duplicate index");
        assertThatThrownBy(() -> reranker.parseScores(
                objectMapper.readTree("{\"results\":[{\"index\":0}]}"), 1))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("non-numeric score");
    }
}
