package com.orbit.app.search;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.orbit.app.inference.HfInferenceClient;
import com.orbit.app.inference.HfInferenceProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Component
@ConditionalOnProperty(name = "orbit.search.reranker-provider", havingValue = "huggingface")
class HfCrossEncoderClient implements CrossEncoderClient {

    private final HfInferenceClient client;
    private final HfInferenceProperties props;
    private final JsonMapper objectMapper;

    HfCrossEncoderClient(HfInferenceClient client, HfInferenceProperties props, JsonMapper objectMapper) {
        props.requiredToken();
        this.client = client;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<Double> score(String query, List<String> documents) {
        ArrayNode inputs = objectMapper.createArrayNode();
        for (String document : documents) {
            ObjectNode pair = inputs.addObject();
            pair.put("text", query);
            pair.put("text_pair", document);
        }
        ObjectNode request = objectMapper.createObjectNode().set("inputs", inputs);
        JsonNode response = client.post(props.rerankerModel(), request);
        return parseScores(response, documents.size());
    }

    List<Double> parseScores(JsonNode response, int expectedCount) {
        if (!response.isArray() || response.isEmpty()) {
            throw new IllegalStateException("HF reranker returned an empty or non-array response");
        }

        List<Double> scores = new ArrayList<>();
        if (response.size() == 1 && response.get(0).isArray()
                && response.get(0).size() == expectedCount
                && allObjects(response.get(0))) {
            appendObjectScores(response.get(0), scores);
        } else if (response.size() == expectedCount && allObjects(response)) {
            appendObjectScores(response, scores);
        } else if (response.size() == expectedCount && allSingletonObjectArrays(response)) {
            for (JsonNode item : response) {
                scores.add(requiredScore(item.get(0)));
            }
        } else {
            throw new IllegalStateException("HF reranker returned an unsupported response shape");
        }

        if (scores.size() != expectedCount) {
            throw new IllegalStateException(
                    "HF reranker returned %d scores, expected %d".formatted(scores.size(), expectedCount));
        }
        return List.copyOf(scores);
    }

    private static boolean allObjects(JsonNode array) {
        for (JsonNode item : array) if (!item.isObject()) return false;
        return true;
    }

    private static boolean allSingletonObjectArrays(JsonNode array) {
        for (JsonNode item : array) {
            if (!item.isArray() || item.size() != 1 || !item.get(0).isObject()) return false;
        }
        return true;
    }

    private static void appendObjectScores(JsonNode array, List<Double> scores) {
        for (JsonNode item : array) scores.add(requiredScore(item));
    }

    private static double requiredScore(JsonNode item) {
        JsonNode score = item.get("score");
        if (score == null || !score.isNumber() || !Double.isFinite(score.doubleValue())) {
            throw new IllegalStateException("HF reranker returned a missing or non-numeric score");
        }
        // The Hugging Face sentence-similarity endpoint already returns the model's
        // normalized score. Applying sigmoid again compresses unrelated and relevant
        // pairs toward 0.5 and makes a meaningful threshold impossible to calibrate.
        return score.doubleValue();
    }
}
