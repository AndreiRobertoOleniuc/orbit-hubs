package com.orbit.app.embedding;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import com.orbit.app.inference.OpenRouterClient;
import com.orbit.app.inference.OpenRouterProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Embeddings from OpenRouter's {@code /embeddings} endpoint.
 * <p>
 * A model learns the difference between a query and a passage in one of two
 * ways, and both are configuration ({@code orbit.openrouter.embedding.*}): the
 * request's {@code input_type} ({@code search_query} for voyage) or a text
 * prefix ({@code "query: "} for e5).
 * The vector size is always asked for explicitly, so a model with a larger
 * native size is cut to the 1024 the schema stores.
 */
@Service
class OpenRouterEmbeddingService implements EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(OpenRouterEmbeddingService.class);

    /** Inputs per request; one OpenAlex onboarding is ~25 chunks. */
    static final int BATCH_SIZE = 32;

    private final OpenRouterClient client;
    private final OpenRouterProperties.Embedding model;
    private final JsonMapper objectMapper;

    OpenRouterEmbeddingService(OpenRouterClient client, OpenRouterProperties openRouterProps, JsonMapper objectMapper) {
        this.client = client;
        this.model = openRouterProps.embedding();
        this.objectMapper = objectMapper;
        log.info("OpenRouter embeddings: model {}, {} dimensions, query prefix \"{}\", passage prefix \"{}\","
                        + " input types \"{}\" / \"{}\"",
                model.model(), model.dimensions(), model.queryPrefix(),
                model.passagePrefix(), model.queryInputType(), model.passageInputType());
    }

    @Override
    public String modelName() {
        return model.model();
    }

    @Override
    public float[] embed(String text, EmbeddingKind kind) {
        return embedAll(List.of(text), kind).getFirst();
    }

    /** One request per {@link #BATCH_SIZE} texts; the response carries one vector per input. */
    @Override
    public List<float[]> embedAll(List<String> texts, EmbeddingKind kind) {
        Assert.notNull(kind, "embedding kind must not be null");
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (int start = 0; start < texts.size(); start += BATCH_SIZE) {
            vectors.addAll(embedBatch(texts.subList(start, Math.min(texts.size(), start + BATCH_SIZE)), kind));
        }
        return vectors;
    }

    private List<float[]> embedBatch(List<String> batch, EmbeddingKind kind) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("model", model.model());
        ArrayNode input = request.putArray("input");
        String prefix = kind == EmbeddingKind.QUERY ? model.queryPrefix() : model.passagePrefix();
        for (String text : batch) {
            Assert.hasText(text, "text to embed must not be blank");
            input.add(prefix + text);
        }
        request.put("dimensions", model.dimensions());
        String inputType = kind == EmbeddingKind.QUERY ? model.queryInputType() : model.passageInputType();
        if (StringUtils.hasText(inputType)) {
            request.put("input_type", inputType);
        }

        JsonNode data = client.post("embeddings", request).get("data");
        if (data == null || !data.isArray() || data.size() != batch.size()) {
            throw new IllegalStateException("OpenRouter embedding returned %d vectors for %d inputs"
                    .formatted(data != null && data.isArray() ? data.size() : 0, batch.size()));
        }
        // Each item names the input it belongs to; do not rely on the order of the list.
        float[][] ordered = new float[batch.size()][];
        for (JsonNode item : data) {
            int index = item.path("index").asInt(-1);
            if (index < 0 || index >= ordered.length || ordered[index] != null) {
                throw new IllegalStateException("OpenRouter embedding returned an invalid or duplicate index");
            }
            ordered[index] = toVector(item.get("embedding"));
        }
        return List.of(ordered);
    }

    private float[] toVector(JsonNode vector) {
        if (vector == null || !vector.isArray() || vector.size() != model.dimensions()) {
            throw new IllegalStateException("OpenRouter embedding model '%s' returned %d dimensions, expected %d"
                    .formatted(model.model(), vector != null && vector.isArray() ? vector.size() : 0,
                            model.dimensions()));
        }
        float[] result = new float[vector.size()];
        for (int i = 0; i < result.length; i++) {
            JsonNode value = vector.get(i);
            if (!value.isNumber() || !Float.isFinite(value.floatValue())) {
                throw new IllegalStateException("OpenRouter embedding contains a non-numeric value at index " + i);
            }
            result[i] = value.floatValue();
        }
        return result;
    }
}
