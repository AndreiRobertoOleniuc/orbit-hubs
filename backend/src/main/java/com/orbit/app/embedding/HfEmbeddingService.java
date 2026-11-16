package com.orbit.app.embedding;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import com.orbit.app.inference.HfInferenceClient;
import com.orbit.app.inference.HfInferenceProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
@ConditionalOnProperty(name = "orbit.embedding.provider", havingValue = "huggingface")
class HfEmbeddingService implements EmbeddingService {

    /** Inputs per request; one OpenAlex onboarding is ~25 chunks. */
    static final int BATCH_SIZE = 32;

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
    public String modelName() {
        return EmbeddingService.canonicalModelName(hfProps.embeddingModel());
    }

    @Override
    public float[] embed(String text, EmbeddingKind kind) {
        Assert.hasText(text, "text to embed must not be blank");
        Assert.notNull(kind, "embedding kind must not be null");
        String prefixed = prefix(text, kind);

        JsonNode response = client.post(hfProps.embeddingModel(),
                objectMapper.createObjectNode().put("inputs", prefixed));
        return toVector(unwrapVector(response));
    }

    /**
     * One request per {@link #BATCH_SIZE} texts: the feature-extraction endpoint
     * takes a list of inputs and returns one vector per input, in order.
     */
    @Override
    public List<float[]> embedAll(List<String> texts, EmbeddingKind kind) {
        Assert.notNull(kind, "embedding kind must not be null");
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (int start = 0; start < texts.size(); start += BATCH_SIZE) {
            List<String> batch = texts.subList(start, Math.min(texts.size(), start + BATCH_SIZE));
            ArrayNode inputs = objectMapper.createArrayNode();
            for (String text : batch) {
                Assert.hasText(text, "text to embed must not be blank");
                inputs.add(prefix(text, kind));
            }
            ObjectNode request = objectMapper.createObjectNode();
            request.set("inputs", inputs);
            JsonNode response = client.post(hfProps.embeddingModel(), request);
            if (!response.isArray() || response.size() != batch.size()) {
                throw new IllegalStateException("HF embedding returned %d vectors for %d inputs"
                        .formatted(response.isArray() ? response.size() : 0, batch.size()));
            }
            for (JsonNode vector : response) {
                if (!vector.isArray()) {
                    throw new IllegalStateException("HF embedding returned a non-array vector in a batch");
                }
                vectors.add(toVector(vector));
            }
        }
        return vectors;
    }

    private String prefix(String text, EmbeddingKind kind) {
        return switch (kind) {
            case QUERY -> embeddingProps.queryPrefix() + text;
            case PASSAGE -> embeddingProps.passagePrefix() + text;
        };
    }

    private float[] toVector(JsonNode vector) {
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
