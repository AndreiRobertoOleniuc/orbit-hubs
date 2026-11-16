package com.orbit.app.inference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class HfInferenceClientTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Test
    void waitsForEstimatedLoadingTimeThenRetries() {
        Queue<InferenceResponse> responses = new ArrayDeque<>(List.of(
                response(503, "{\"error\":\"loading\",\"estimated_time\":0.5}"),
                response(200, "[1,2,3]")));
        AtomicLong nanos = new AtomicLong();
        AtomicLong sleptMillis = new AtomicLong();
        HfInferenceClient client = client(responses, Duration.ofSeconds(5), nanos, millis -> {
            sleptMillis.addAndGet(millis);
            nanos.addAndGet(Duration.ofMillis(millis).toNanos());
        });

        assertThat(client.post("model", objectMapper.createObjectNode().put("inputs", "x")).size()).isEqualTo(3);
        assertThat(sleptMillis.get()).isEqualTo(750);
        assertThat(responses).isEmpty();
    }

    @Test
    void honorsRetryAfterBeforeEstimatedTime() {
        Queue<InferenceResponse> responses = new ArrayDeque<>(List.of(
                new InferenceResponse(503, Map.of("Retry-After", List.of("2")),
                        "{\"estimated_time\":20}"),
                response(200, "[]")));
        AtomicLong nanos = new AtomicLong();
        AtomicLong sleptMillis = new AtomicLong();
        HfInferenceClient client = client(responses, Duration.ofSeconds(5), nanos, millis -> {
            sleptMillis.addAndGet(millis);
            nanos.addAndGet(Duration.ofMillis(millis).toNanos());
        });

        client.post("model", objectMapper.createObjectNode());

        assertThat(sleptMillis.get()).isEqualTo(2_000);
    }

    @Test
    void stopsAtConfiguredDeadline() {
        Queue<InferenceResponse> responses = new ArrayDeque<>(List.of(
                response(503, "{\"estimated_time\":30}")));
        AtomicLong nanos = new AtomicLong();
        HfInferenceClient client = client(responses, Duration.ofSeconds(1), nanos,
                millis -> nanos.addAndGet(Duration.ofMillis(millis).toNanos()));

        assertThatThrownBy(() -> client.post("model", objectMapper.createObjectNode()))
                .isInstanceOf(InferenceUnavailableException.class)
                .hasMessageContaining("PT1S");
    }

    @Test
    void aCallersDeadlineCutsTheWaitShort() {
        Queue<InferenceResponse> responses = new ArrayDeque<>(List.of(
                response(503, "{\"estimated_time\":30}"), response(200, "[]")));
        AtomicLong nanos = new AtomicLong();
        AtomicLong slept = new AtomicLong();
        HfInferenceClient client = client(responses, Duration.ofMinutes(2), nanos, millis -> {
            slept.addAndGet(millis);
            nanos.addAndGet(Duration.ofMillis(millis).toNanos());
        });

        long deadline = nanos.get() + Duration.ofSeconds(5).toNanos();
        assertThatThrownBy(() -> InferenceDeadline.within(deadline,
                () -> client.post("model", objectMapper.createObjectNode())))
                .isInstanceOf(InferenceUnavailableException.class)
                .hasMessageContaining("deadline");
        assertThat(slept.get()).isEqualTo(5_000);       // not the 30 s the provider asked for
        assertThat(InferenceDeadline.current()).isEmpty();
    }

    @Test
    void aCallersDeadlineNeverExtendsTheConfiguredWait() {
        Queue<InferenceResponse> responses = new ArrayDeque<>(List.of(
                response(503, "{\"estimated_time\":30}")));
        AtomicLong nanos = new AtomicLong();
        HfInferenceClient client = client(responses, Duration.ofSeconds(1), nanos,
                millis -> nanos.addAndGet(Duration.ofMillis(millis).toNanos()));

        long deadline = nanos.get() + Duration.ofMinutes(10).toNanos();
        assertThatThrownBy(() -> InferenceDeadline.within(deadline,
                () -> client.post("model", objectMapper.createObjectNode())))
                .isInstanceOf(InferenceUnavailableException.class);
        assertThat(nanos.get()).isEqualTo(Duration.ofSeconds(1).toNanos());
    }

    @Test
    void doesNotRetryAuthenticationFailure() {
        Queue<InferenceResponse> responses = new ArrayDeque<>(List.of(response(401, "unauthorized")));
        AtomicLong nanos = new AtomicLong();
        HfInferenceClient client = client(responses, Duration.ofSeconds(5), nanos, millis -> {
            throw new AssertionError("must not sleep");
        });

        assertThatThrownBy(() -> client.post("model", objectMapper.createObjectNode()))
                .isInstanceOf(InferenceUnavailableException.class)
                .hasMessageContaining("401");
        assertThat(responses).isEmpty();
    }

    private HfInferenceClient client(Queue<InferenceResponse> responses, Duration maxWait,
            AtomicLong nanos, InferenceSleeper sleeper) {
        HfInferenceProperties props = new HfInferenceProperties(
                "hf_test", "https://example.test/models", "embedding", "reranker",
                maxWait, Duration.ofSeconds(1), Duration.ofSeconds(1));
        return new HfInferenceClient(props, objectMapper,
                (uri, token, body, timeout) -> responses.remove(), sleeper, nanos::get);
    }

    private static InferenceResponse response(int status, String body) {
        return new InferenceResponse(status, Map.of(), body);
    }
}
