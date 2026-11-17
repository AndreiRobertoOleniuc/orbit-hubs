package com.orbit.app.inference;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

/**
 * OpenRouter serves every model Orbit calls — the LLM, the embedding model and
 * the reranker — behind one key and one bill ({@code orbit.openrouter.*}).
 * <p>
 * The model choices have no default here: application.yaml is the one place
 * they are written, and the application does not start when one is missing.
 * Connection settings keep their defaults.
 *
 * @param dataCollection {@code deny} routes only to providers that do not keep
 *                       or train on what is sent (profiles, CVs, queries);
 *                       {@code allow} lifts that; empty sends no preference
 * @param maxWait        how long a background call (ingestion, re-embedding)
 *                       waits out an overloaded or rate-limited model
 */
@ConfigurationProperties(prefix = "orbit.openrouter")
public record OpenRouterProperties(
        @DefaultValue("") String apiKey,
        @DefaultValue("https://openrouter.ai/api/v1") String baseUrl,
        String dataCollection,
        @DefaultValue("PT2M") Duration maxWait,
        @DefaultValue("PT5S") Duration connectTimeout,
        @DefaultValue("PT30S") Duration requestTimeout,
        @DefaultValue Chat chat,
        @DefaultValue Embedding embedding,
        @DefaultValue Reranker reranker) {

    public OpenRouterProperties {
        if (dataCollection == null) throw missing("data-collection");
    }

    /**
     * @param fallbackModels models OpenRouter answers with, in this order, when
     *                       {@code model} is rate limited or down — within the
     *                       same request, so the caller does not wait and retry.
     *                       Needed: gemini-2.5-flash-lite has one provider and a
     *                       rate limit shared by all OpenRouter customers (24 of
     *                       60 calls in bursts of 20 refused, none with these
     *                       fallbacks, 2026-11-16)
     * @param maxWait        how long one LLM call waits out an unavailable model.
     *                       Short: every caller has its own fallback (the query as
     *                       typed, the existing summary) or retries later.
     */
    public record Chat(
            String model,
            List<String> fallbackModels,
            Double temperature,
            Integer maxTokens,
            @DefaultValue("PT20S") Duration maxWait) {

        public Chat {
            if (!StringUtils.hasText(model)) throw missing("chat.model");
            if (fallbackModels == null) throw missing("chat.fallback-models");
            if (temperature == null) throw missing("chat.temperature");
            if (maxTokens == null) throw missing("chat.max-tokens");
            fallbackModels = List.copyOf(fallbackModels);
        }
    }

    /**
     * A query and a passage are told apart by {@code input_type} or by a text
     * prefix, whichever the model uses — set one pair, leave the other empty.
     *
     * @param dimensions       the size every vector column stores ({@code vector(1024)});
     *                         a model with a larger native size is asked for this one
     * @param queryInputType   sent as {@code input_type} with a query ({@code search_query} for voyage)
     * @param passageInputType the same for passages ({@code search_document})
     * @param queryPrefix      put in front of a query instead ({@code "query: "} for e5)
     * @param passagePrefix    the same for passages ({@code "passage: "} for e5)
     */
    public record Embedding(
            String model,
            Integer dimensions,
            @DefaultValue("") String queryInputType,
            @DefaultValue("") String passageInputType,
            @DefaultValue("") String queryPrefix,
            @DefaultValue("") String passagePrefix) {

        public Embedding {
            if (!StringUtils.hasText(model)) throw missing("embedding.model");
            if (dimensions == null) throw missing("embedding.dimensions");
        }
    }

    public record Reranker(String model) {

        public Reranker {
            if (!StringUtils.hasText(model)) throw missing("reranker.model");
        }
    }

    private static IllegalStateException missing(String key) {
        return new IllegalStateException("orbit.openrouter." + key + " must be set in application.yaml");
    }

    public String requiredApiKey() {
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("OPENROUTER_API_KEY must be set: OpenRouter serves the LLM,"
                    + " the embedding model and the reranker");
        }
        return apiKey;
    }
}
