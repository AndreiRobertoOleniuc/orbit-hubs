package com.orbit.app.inference;

import java.net.URI;
import java.time.Duration;
import java.util.function.LongSupplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The one way Orbit talks to OpenRouter: chat completions, embeddings and
 * reranking are JSON POSTs to {@code /chat/completions}, {@code /embeddings}
 * and {@code /rerank} under the same base URL and key.
 * <p>
 * Every request carries the configured data-collection preference, so profile
 * text only goes to providers that do not keep it. An overloaded or
 * rate-limited model is waited out until the call's deadline (see
 * {@link RetryingInferenceCall}); a refused request (bad key, no credits)
 * fails at once with OpenRouter's own message.
 */
@Component
@EnableConfigurationProperties(OpenRouterProperties.class)
public class OpenRouterClient {

    private final OpenRouterProperties props;
    private final RetryingInferenceCall call;

    @Autowired
    public OpenRouterClient(OpenRouterProperties props, JsonMapper objectMapper) {
        this(props, objectMapper, RetryingInferenceCall.javaTransport(props.connectTimeout()),
                Thread::sleep, System::nanoTime);
    }

    OpenRouterClient(OpenRouterProperties props, JsonMapper objectMapper,
            InferenceTransport transport, InferenceSleeper sleeper, LongSupplier nanoTime) {
        props.requiredApiKey();
        this.props = props;
        this.call = new RetryingInferenceCall("OpenRouter", props.requestTimeout(), OpenRouterClient::isRetryable,
                objectMapper, transport, sleeper, nanoTime);
    }

    /** POST with the wait of a background call ({@code orbit.openrouter.max-wait}). */
    public JsonNode post(String path, ObjectNode requestBody) {
        return post(path, requestBody, props.maxWait());
    }

    /**
     * @param path        the endpoint under the base URL: {@code embeddings}, {@code rerank}, {@code chat/completions}
     * @param requestBody the request, with its {@code model}; the routing preference is added here
     * @param maxWait     how long to wait out an unavailable model, retries included
     */
    public JsonNode post(String path, ObjectNode requestBody, Duration maxWait) {
        if (StringUtils.hasText(props.dataCollection())) {
            requestBody.putObject("provider").put("data_collection", props.dataCollection());
        }
        String model = requestBody.path("model").asString("?");
        URI uri = URI.create(RetryingInferenceCall.stripTrailingSlash(props.baseUrl()) + "/" + path);
        JsonNode response = call.post(uri, props.requiredApiKey(), model, requestBody, maxWait);
        // A provider that fails after OpenRouter accepted the request is reported in a 200 body.
        if (response.has("error")) {
            throw new InferenceUnavailableException("OpenRouter model " + model + " failed"
                    + RetryingInferenceCall.errorMessage(response).map(message -> ": " + message).orElse(""));
        }
        return response;
    }

    /**
     * Timeouts, rate limits and provider outages pass; a bad request, a bad key
     * (401), no credits (402) and a blocked request (403) do not.
     */
    private static boolean isRetryable(int status) {
        return status == 408 || status == 429 || status == 500 || status == 502 || status == 503
                || status == 504 || status == 524 || status == 529;
    }
}
