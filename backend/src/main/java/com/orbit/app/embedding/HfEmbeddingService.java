package com.orbit.app.embedding;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import com.orbit.app.inference.HfInferenceClient;
import com.orbit.app.inference.HfInferenceProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
@ConditionalOnProperty(name = "orbit.embedding.provider", havingValue = "huggingface")
class HfEmbeddingService implements EmbeddingService {

    private final HfInferenceClient client;
    private final HfInferenceProperties hfProps;
    private final EmbeddingProperties embeddingProps;
    private final JsonMapper objectMapper;

    HfEmbeddingService(HfInferenceClient client, HfInferenceProperties hfProps,
            EmbeddingProperties embeddingProps, JsonMapper objectMapper) {
        hfProps.requiredToken();
        this.client = client;
        this.hfProps = hfProps;
        this.embeddingProps = embeddingProps;
        this.objectMapper = objectMapper;
    }

    @Override
    public float[] embed(String text, EmbeddingKind kind) {
        Assert.hasText(text, "text to embed must not be blank");
        Assert.notNull(kind, "embedding kind must not be null");
        String prefixed = switch (kind) {
            case QUERY -> embeddingProps.queryPrefix() + text;
            case PASSAGE -> embeddingProps.passagePrefix() + text;
        };

        JsonNode response = client.post(hfProps.embeddingModel(),
                objectMapper.createObjectNode().put("inputs", prefixed));
        JsonNode vector = unwrapVector(response);
        if (vector.size() != embeddingProps.dimensions()) {
            throw new IllegalStateException("HF embedding model '%s' returned %d dimensions, expected %d"
                    .formatted(hfProps.embeddingModel(), vector.size(), embeddingProps.dimensions()));
        }
        float[] result = new float[vector.size()];
        for (int i = 0; i < vector.size(); i++) {
            if (!vector.get(i).isNumber()) {
                throw new IllegalStateException("HF embedding response contains a non-numeric value at index " + i);
            }
            result[i] = vector.get(i).floatValue();
            if (!Float.isFinite(result[i])) {
                throw new IllegalStateException("HF embedding response contains a non-finite value at index " + i);
            }
        }
        return result;
    }

    private JsonNode unwrapVector(JsonNode response) {
        if (!response.isArray() || response.isEmpty()) {
            throw new IllegalStateException("HF embedding returned an empty or non-array response");
        }
        if (response.get(0).isArray()) {
            if (response.size() != 1) {
                throw new IllegalStateException("HF embedding returned multiple vectors for one input");
            }
            return response.get(0);
        }
        return response;
    }
}
