package com.orbit.app.inference;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class HfInferenceClient {

    private static final Logger log = LoggerFactory.getLogger(HfInferenceClient.class);
    private static final Duration MAX_SINGLE_DELAY = Duration.ofSeconds(30);

    private final HfInferenceProperties props;
    private final JsonMapper objectMapper;
    private final Transport transport;
    private final Sleeper sleeper;
    private final LongSupplier nanoTime;

    @Autowired
    public HfInferenceClient(HfInferenceProperties props, JsonMapper objectMapper) {
        this(props, objectMapper, javaTransport(props), Thread::sleep, System::nanoTime);
    }

    HfInferenceClient(HfInferenceProperties props, JsonMapper objectMapper,
            Transport transport, Sleeper sleeper, LongSupplier nanoTime) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.transport = transport;
        this.sleeper = sleeper;
        this.nanoTime = nanoTime;
    }

    public JsonNode post(String model, JsonNode requestBody) {
        String token = props.requiredToken();
        URI uri = URI.create(stripTrailingSlash(props.baseUrl()) + "/" + model);
        long deadline = nanoTime.getAsLong() + props.maxWait().toNanos();
        int retry = 0;

        while (true) {
            try {
                Response response = transport.post(uri, token, objectMapper.writeValueAsString(requestBody), props.requestTimeout());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return objectMapper.readTree(response.body());
                }
                if (!isRetryable(response.statusCode())) {
                    throw new InferenceUnavailableException(
                            "Hugging Face inference failed with HTTP " + response.statusCode());
                }
                waitBeforeRetry(model, response, retry++, deadline);
            } catch (InferenceUnavailableException ex) {
                throw ex;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new InferenceUnavailableException("Interrupted while waiting for Hugging Face model " + model, ex);
            } catch (IOException ex) {
                waitAfterTransportFailure(model, retry++, deadline, ex);
            }
        }
    }

    private void waitAfterTransportFailure(String model, int retry, long deadline, IOException cause) {
        try {
            waitFor(model, fallbackDelay(retry), deadline, "transport failure");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new InferenceUnavailableException("Interrupted while waiting for Hugging Face model " + model, ex);
        } catch (InferenceUnavailableException ex) {
            throw new InferenceUnavailableException(ex.getMessage(), cause);
        }
    }

    private void waitBeforeRetry(String model, Response response, int retry, long deadline) throws InterruptedException {
        Duration delay = retryAfter(response).orElseGet(() -> estimatedLoadingTime(response)
                .orElseGet(() -> fallbackDelay(retry)));
        waitFor(model, delay, deadline, "HTTP " + response.statusCode());
    }

    private void waitFor(String model, Duration requestedDelay, long deadline, String reason) throws InterruptedException {
        long remainingNanos = deadline - nanoTime.getAsLong();
        if (remainingNanos <= 0) {
            throw new InferenceUnavailableException(
                    "Hugging Face model " + model + " was unavailable for " + props.maxWait());
        }
        Duration remaining = Duration.ofNanos(remainingNanos);
        Duration delay = min(min(nonNegative(requestedDelay), MAX_SINGLE_DELAY), remaining);
        log.info("Hugging Face model {} unavailable ({}); retrying in {} ms", model, reason, delay.toMillis());
        sleeper.sleep(delay.toMillis());
        if (nanoTime.getAsLong() >= deadline) {
            throw new InferenceUnavailableException(
                    "Hugging Face model " + model + " was unavailable for " + props.maxWait());
        }
    }

    private Optional<Duration> estimatedLoadingTime(Response response) {
        if (response.statusCode() != 503 || response.body() == null || response.body().isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode value = objectMapper.readTree(response.body()).get("estimated_time");
            if (value != null && value.isNumber()) {
                long millis = Math.max(0L, Math.round(value.doubleValue() * 1000.0) + 250L);
                return Optional.of(Duration.ofMillis(millis));
            }
        } catch (RuntimeException ignored) {
            // A 503 body is provider-controlled; fall back when it is not JSON.
        }
        return Optional.empty();
    }

    private Optional<Duration> retryAfter(Response response) {
        Optional<String> header = response.firstHeader("Retry-After");
        if (header.isEmpty()) return Optional.empty();
        String value = header.get().trim();
        try {
            return Optional.of(Duration.ofSeconds(Math.max(0, Long.parseLong(value))));
        } catch (NumberFormatException ignored) {
            try {
                Duration until = Duration.between(ZonedDateTime.now(),
                        ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME));
                return Optional.of(nonNegative(until));
            } catch (DateTimeParseException ignoredDate) {
                return Optional.empty();
            }
        }
    }

    private static Duration fallbackDelay(int retry) {
        long baseMillis = Math.min(10_000L, 1_000L << Math.min(retry, 4));
        double jitter = ThreadLocalRandom.current().nextDouble(0.8, 1.2);
        return Duration.ofMillis(Math.round(baseMillis * jitter));
    }

    private static boolean isRetryable(int status) {
        return status == 429 || status == 502 || status == 503 || status == 504;
    }

    private static Duration nonNegative(Duration value) {
        return value.isNegative() ? Duration.ZERO : value;
    }

    private static Duration min(Duration left, Duration right) {
        return left.compareTo(right) <= 0 ? left : right;
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static Transport javaTransport(HfInferenceProperties props) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(props.connectTimeout()).build();
        return (uri, token, body, timeout) -> {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), response.headers().map(), response.body());
        };
    }

    record Response(int statusCode, Map<String, List<String>> headers, String body) {
        Optional<String> firstHeader(String name) {
            return headers.entrySet().stream()
                    .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                    .flatMap(entry -> entry.getValue().stream())
                    .findFirst();
        }
    }

    @FunctionalInterface
    interface Transport {
        Response post(URI uri, String token, String body, Duration timeout) throws IOException, InterruptedException;
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }
}
