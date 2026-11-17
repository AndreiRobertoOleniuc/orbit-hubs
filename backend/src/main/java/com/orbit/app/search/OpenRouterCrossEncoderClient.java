package com.orbit.app.search;

import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;

import com.orbit.app.inference.OpenRouterClient;
import com.orbit.app.inference.OpenRouterProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Reranking through OpenRouter's {@code /rerank} endpoint: one request with
 * the query and every passage, answered with a relevance score per passage.
 * Scores are only comparable within one model — the relevance threshold
 * ({@code orbit.search.reranker-threshold}) belongs to the configured model.
 */
@Component
class OpenRouterCrossEncoderClient implements CrossEncoderClient {

    private final OpenRouterClient client;
    private final String model;
    private final JsonMapper objectMapper;

    OpenRouterCrossEncoderClient(OpenRouterClient client, OpenRouterProperties props, JsonMapper objectMapper) {
        this.client = client;
        this.model = props.reranker().model();
        this.objectMapper = objectMapper;
    }

    @Override
    public List<Double> score(String query, List<String> documents) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("model", model);
        request.put("query", query);
        ArrayNode list = request.putArray("documents");
        documents.forEach(list::add);
        return parseScores(client.post("rerank", request), documents.size());
    }

    /** The response lists passages best first; put the scores back into input order. */
    List<Double> parseScores(JsonNode response, int expectedCount) {
        JsonNode results = response.get("results");
        if (results == null || !results.isArray() || results.size() != expectedCount) {
            throw new IllegalStateException("OpenRouter reranker returned %d scores, expected %d"
                    .formatted(results != null && results.isArray() ? results.size() : 0, expectedCount));
        }
        Double[] scores = new Double[expectedCount];
        for (JsonNode result : results) {
            int index = result.path("index").asInt(-1);
            JsonNode score = result.get("relevance_score");
            if (index < 0 || index >= expectedCount || scores[index] != null) {
                throw new IllegalStateException("OpenRouter reranker returned an invalid or duplicate index");
            }
            if (score == null || !score.isNumber() || !Double.isFinite(score.doubleValue())) {
                throw new IllegalStateException("OpenRouter reranker returned a missing or non-numeric score");
            }
            scores[index] = score.doubleValue();
        }
        return List.copyOf(Arrays.asList(scores));
    }
}
