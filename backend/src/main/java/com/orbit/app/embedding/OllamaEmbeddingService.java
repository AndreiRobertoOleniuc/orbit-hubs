package com.orbit.app.embedding;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

@Service
@ConditionalOnProperty(name = "orbit.embedding.provider", havingValue = "ollama", matchIfMissing = true)
class OllamaEmbeddingService implements EmbeddingService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingProperties props;

    OllamaEmbeddingService(EmbeddingModel embeddingModel, EmbeddingProperties props) {
        this.embeddingModel = embeddingModel;
        this.props = props;
    }

    @Override
    public float[] embed(String text, EmbeddingKind kind) {
        Assert.hasText(text, "text to embed must not be blank");
        Assert.notNull(kind, "embedding kind must not be null");

        String prefixed = switch (kind) {
            case QUERY -> props.queryPrefix() + text;
            case PASSAGE -> props.passagePrefix() + text;
        };

        float[] vector = embeddingModel.embed(prefixed);

        if (vector.length != props.dimensions()) {
            throw new IllegalStateException(
                    "Embedding model '%s' returned %d dimensions, expected %d".formatted(props.model(), vector.length, props.dimensions()));
        }
        return vector;
    }
}
