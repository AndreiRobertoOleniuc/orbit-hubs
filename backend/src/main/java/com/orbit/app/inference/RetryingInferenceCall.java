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
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntPredicate;
import java.util.function.LongSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * One JSON POST to a hosted model API, repeated while the provider says the
 * model is loading, overloaded or rate limited — until a deadline. Shared by
 * the provider clients ({@link OpenRouterClient}, {@link HfInferenceClient}),
 * which differ only in address, credentials and which statuses are worth a retry.
 * <p>
 * The deadline is the earlier of the call's own maximum wait and the caller's
 * {@link InferenceDeadline}; the wait between attempts follows the provider's
 * {@code Retry-After} header when it sends one.
 */
final class RetryingInferenceCall {

    private static final Logger log = LoggerFactory.getLogger(RetryingInferenceCall.class);
    private static final Duration MAX_SINGLE_DELAY = Duration.ofSeconds(30);
    private static final int MAX_ERROR_CHARS = 200;

    private final String provider;
    private final Duration requestTimeout;
    private final IntPredicate retryable;
    private final JsonMapper objectMapper;
    private final InferenceTransport transport;
    private final InferenceSleeper sleeper;
    private final LongSupplier nanoTime;

    /**
     * @param provider  the provider's name, for messages ("OpenRouter")
     * @param retryable the HTTP statuses that mean "try again later"
     */
    RetryingInferenceCall(String provider, Duration requestTimeout, IntPredicate retryable, JsonMapper objectMapper,
            InferenceTransport transport, InferenceSleeper sleeper, LongSupplier nanoTime) {
        this.provider = provider;
        this.requestTimeout = requestTimeout;
        this.retryable = retryable;
        this.objectMapper = objectMapper;
        this.transport = transport;
        this.sleeper = sleeper;
        this.nanoTime = nanoTime;
    }

    /**
     * @param model   the model asked for, for messages
     * @param maxWait how long this call may take in all, retries included
     * @throws InferenceUnavailableException when the provider refuses the request or the deadline passes
     */
    JsonNode post(URI uri, String token, String model, JsonNode requestBody, Duration maxWait) {
        long deadline = nanoTime.getAsLong() + maxWait.toNanos();
        // A caller with someone waiting (a search) sets a tighter deadline.
        OptionalLong callerDeadline = InferenceDeadline.current();
        if (callerDeadline.isPresent() && callerDeadline.getAsLong() - deadline < 0) {
            deadline = callerDeadline.getAsLong();
        }
        int retry = 0;

        while (true) {
            try {
                InferenceResponse response = transport.post(
                        uri, token, objectMapper.writeValueAsString(requestBody), timeout(model, deadline, maxWait));
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return objectMapper.readTree(response.body());
                }
                if (!retryable.test(response.statusCode())) {
                    throw new InferenceUnavailableException(provider + " inference failed with HTTP "
                            + response.statusCode() + errorDetail(response));
                }
                waitBeforeRetry(model, response, retry++, deadline, maxWait);
            } catch (InferenceUnavailableException ex) {
                throw ex;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new InferenceUnavailableException("Interrupted while waiting for " + provider + " model " + model, ex);
            } catch (IOException ex) {
                waitAfterTransportFailure(model, retry++, deadline, maxWait, ex);
            }
        }
    }

    /** The per-request timeout, cut to what is left before {@code deadline}. */
    private Duration timeout(String model, long deadline, Duration maxWait) {
        long remainingNanos = deadline - nanoTime.getAsLong();
        if (remainingNanos <= 0) {
            throw outOfTime(model, maxWait);
        }
        return min(requestTimeout, Duration.ofNanos(remainingNanos));
    }

    private InferenceUnavailableException outOfTime(String model, Duration maxWait) {
        String limit = InferenceDeadline.current().isPresent()
                ? "the caller's deadline or " + maxWait
                : maxWait.toString();
        return new InferenceUnavailableException(provider + " model " + model + " was unavailable for " + limit);
    }

    private void waitAfterTransportFailure(String model, int retry, long deadline, Duration maxWait, IOException cause) {
        try {
            waitFor(model, fallbackDelay(retry), deadline, maxWait, "transport failure");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new InferenceUnavailableException("Interrupted while waiting for " + provider + " model " + model, ex);
        } catch (InferenceUnavailableException ex) {
            throw new InferenceUnavailableException(ex.getMessage(), cause);
        }
    }

    private void waitBeforeRetry(String model, InferenceResponse response, int retry, long deadline, Duration maxWait)
            throws InterruptedException {
        Duration delay = retryAfter(response).orElseGet(() -> estimatedLoadingTime(response)
                .orElseGet(() -> fallbackDelay(retry)));
        waitFor(model, delay, deadline, maxWait, "HTTP " + response.statusCode());
    }

    private void waitFor(String model, Duration requestedDelay, long deadline, Duration maxWait, String reason)
            throws InterruptedException {
        long remainingNanos = deadline - nanoTime.getAsLong();
        if (remainingNanos <= 0) {
            throw outOfTime(model, maxWait);
        }
        Duration remaining = Duration.ofNanos(remainingNanos);
        Duration delay = min(min(nonNegative(requestedDelay), MAX_SINGLE_DELAY), remaining);
        log.info("{} model {} unavailable ({}); retrying in {} ms", provider, model, reason, delay.toMillis());
        sleeper.sleep(delay.toMillis());
        if (nanoTime.getAsLong() - deadline >= 0) {
            throw outOfTime(model, maxWait);
        }
    }

    /** Hugging Face says how long a cold model still needs to load: {@code {"estimated_time": 12.5}}. */
    private Optional<Duration> estimatedLoadingTime(InferenceResponse response) {
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

    private Optional<Duration> retryAfter(InferenceResponse response) {
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

    /**
     * The provider's own words for a refused request (": Insufficient credits"),
     * so the log says why; empty when the body carries none.
     */
    private String errorDetail(InferenceResponse response) {
        return errorMessage(objectMapper, response.body()).map(message -> ": " + message).orElse("");
    }

    /** The message of an error body: {@code {"error": {"message": "…"}}} or {@code {"error": "…"}}. */
    static Optional<String> errorMessage(JsonMapper objectMapper, String body) {
        if (body == null || body.isBlank()) return Optional.empty();
        try {
            return errorMessage(objectMapper.readTree(body));
        } catch (RuntimeException notJson) {
            return Optional.empty();
        }
    }

    static Optional<String> errorMessage(JsonNode body) {
        JsonNode error = body == null ? null : body.get("error");
        if (error == null) return Optional.empty();
        JsonNode message = error.isObject() ? error.get("message") : error;
        if (message == null || !message.isString() || message.asString().isBlank()) return Optional.empty();
        String text = message.asString().strip();
        return Optional.of(text.length() <= MAX_ERROR_CHARS ? text : text.substring(0, MAX_ERROR_CHARS) + "…");
    }

    private static Duration fallbackDelay(int retry) {
        long baseMillis = Math.min(10_000L, 1_000L << Math.min(retry, 4));
        double jitter = ThreadLocalRandom.current().nextDouble(0.8, 1.2);
        return Duration.ofMillis(Math.round(baseMillis * jitter));
    }

    private static Duration nonNegative(Duration value) {
        return value.isNegative() ? Duration.ZERO : value;
    }

    private static Duration min(Duration left, Duration right) {
        return left.compareTo(right) <= 0 ? left : right;
    }

    /** The JDK HTTP client, sending the token as a bearer credential. */
    static InferenceTransport javaTransport(Duration connectTimeout) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        return (uri, token, body, timeout) -> {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return new InferenceResponse(response.statusCode(), response.headers().map(), response.body());
        };
    }

    static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
