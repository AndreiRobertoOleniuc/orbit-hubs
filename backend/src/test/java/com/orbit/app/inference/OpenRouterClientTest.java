package com.orbit.app.inference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class OpenRouterClientTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();
    private final Queue<InferenceResponse> responses = new ArrayDeque<>();
    private final List<URI> uris = new ArrayList<>();
    private final List<String> tokens = new ArrayList<>();
    private final List<JsonNode> bodies = new ArrayList<>();
    private final AtomicLong nanos = new AtomicLong();
    private final AtomicLong sleptMillis = new AtomicLong();

    @Test
    void postsToThePathWithTheKeyAndTheDataCollectionPreference() {
        responses.add(response(200, "{\"data\":[]}"));

        JsonNode answer = client("deny").post("embeddings", request());

        assertThat(answer.has("data")).isTrue();
        assertThat(uris).containsExactly(URI.create("https://openrouter.test/api/v1/embeddings"));
        assertThat(tokens).containsExactly("sk-test");
        assertThat(bodies.getFirst().get("model").asString()).isEqualTo("some/model");
        assertThat(bodies.getFirst().get("provider").get("data_collection").asString()).isEqualTo("deny");
    }

    @Test
    void sendsNoRoutingPreferenceWhenNoneIsConfigured() {
        responses.add(response(200, "{}"));

        client("").post("rerank", request());

        assertThat(bodies.getFirst().has("provider")).isFalse();
    }

    @Test
    void waitsOutARateLimitForAsLongAsOpenRouterAsks() {
        responses.add(new InferenceResponse(429, Map.of("Retry-After", List.of("3")),
                "{\"error\":{\"code\":429,\"message\":\"Rate limit exceeded\"}}"));
        responses.add(response(200, "{\"results\":[]}"));

        client("deny").post("rerank", request());

        assertThat(sleptMillis.get()).isEqualTo(3_000);
        assertThat(responses).isEmpty();
    }

    @Test
    void anAccountWithoutCreditsFailsAtOnceWithOpenRoutersMessage() {
        responses.add(response(402, "{\"error\":{\"code\":402,\"message\":\"Insufficient credits\"}}"));
        responses.add(response(200, "{}"));

        assertThatThrownBy(() -> client("deny").post("chat/completions", request()))
                .isInstanceOf(InferenceUnavailableException.class)
                .hasMessageContaining("402")
                .hasMessageContaining("Insufficient credits");
        assertThat(responses).hasSize(1);
        assertThat(sleptMillis.get()).isZero();
    }

    @Test
    void aProviderErrorReportedInASuccessfulResponseIsAFailure() {
        responses.add(response(200, "{\"error\":{\"code\":502,\"message\":\"Provider returned error\"}}"));

        assertThatThrownBy(() -> client("deny").post("embeddings", request()))
                .isInstanceOf(InferenceUnavailableException.class)
                .hasMessageContaining("some/model")
                .hasMessageContaining("Provider returned error");
    }

    @Test
    void aCallCanBeGivenAShorterWaitThanBackgroundWork() {
        responses.add(response(503, "{\"error\":{\"code\":503,\"message\":\"No provider available\"}}"));

        assertThatThrownBy(() -> client("deny").post("chat/completions", request(), Duration.ofMillis(500)))
                .isInstanceOf(InferenceUnavailableException.class)
                .hasMessageContaining("PT0.5S");
        assertThat(sleptMillis.get()).isEqualTo(500);
    }

    @Test
    void refusesToStartWithoutAKey() {
        assertThatThrownBy(() -> new OpenRouterClient(props("", "deny"), objectMapper))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OPENROUTER_API_KEY");
    }

    private OpenRouterClient client(String dataCollection) {
        InferenceTransport transport = (uri, token, body, timeout) -> {
            uris.add(uri);
            tokens.add(token);
            bodies.add(objectMapper.readTree(body));
            return responses.remove();
        };
        return new OpenRouterClient(props("sk-test", dataCollection), objectMapper, transport, millis -> {
            sleptMillis.addAndGet(millis);
            nanos.addAndGet(Duration.ofMillis(millis).toNanos());
        }, nanos::get);
    }

    private static OpenRouterProperties props(String apiKey, String dataCollection) {
        return new OpenRouterProperties(apiKey, "https://openrouter.test/api/v1/", dataCollection,
                Duration.ofMinutes(2), Duration.ofSeconds(5), Duration.ofSeconds(30),
                new OpenRouterProperties.Chat("chat/model", List.of(), 0.3, 500, Duration.ofSeconds(20)),
                new OpenRouterProperties.Embedding("embed/model", "", ""),
                new OpenRouterProperties.Reranker("rerank/model"));
    }

    private ObjectNode request() {
        return objectMapper.createObjectNode().put("model", "some/model");
    }

    private static InferenceResponse response(int status, String body) {
        return new InferenceResponse(status, Map.of(), body);
    }
}
