package com.orbit.app.inference;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

/**
 * OpenRouter serves every model Orbit calls — the LLM, the embedding model and
 * the reranker — behind one key and one bill ({@code orbit.openrouter.*}).
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
        @DefaultValue("deny") String dataCollection,
        @DefaultValue("PT2M") Duration maxWait,
        @DefaultValue("PT5S") Duration connectTimeout,
        @DefaultValue("PT30S") Duration requestTimeout,
        @DefaultValue Chat chat,
        @DefaultValue Embedding embedding,
        @DefaultValue Reranker reranker) {

    /**
     * @param fallbackModels models OpenRouter answers with, in this order, when
     *                       {@code model} is rate limited or down — within the
     *                       same request, so the caller does not wait and retry
     * @param maxWait        how long one LLM call waits out an unavailable model.
     *                       Short: every caller has its own fallback (the query as
     *                       typed, the existing summary) or retries later.
     */
    public record Chat(
            @DefaultValue("google/gemini-2.5-flash-lite") String model,
            @DefaultValue List<String> fallbackModels,
            @DefaultValue("0.3") double temperature,
            @DefaultValue("500") int maxTokens,
            @DefaultValue("PT20S") Duration maxWait) {
    }

    /**
     * @param queryInputType   sent as {@code input_type} with a query, for models
     *                         that tell queries and passages apart that way
     *                         ({@code search_query}); empty for models prompted by
     *                         a text prefix instead ({@code orbit.embedding.query-prefix})
     * @param passageInputType the same for passages ({@code search_document})
     */
    public record Embedding(
            @DefaultValue("mongodb/voyage-4-lite") String model,
            @DefaultValue("search_query") String queryInputType,
            @DefaultValue("search_document") String passageInputType) {
    }

    public record Reranker(@DefaultValue("cohere/rerank-5-fast") String model) {
    }

    public String requiredApiKey() {
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("OPENROUTER_API_KEY must be set: OpenRouter serves the LLM"
                    + " and, unless another provider is configured, the embedding model and the reranker");
        }
        return apiKey;
    }
}
