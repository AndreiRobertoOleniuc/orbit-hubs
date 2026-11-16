package com.orbit.app.embedding;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

@Service
@ConditionalOnProperty(name = "orbit.embedding.provider", havingValue = "ollama", matchIfMissing = true)
class OllamaEmbeddingService implements EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(OllamaEmbeddingService.class);

    private final EmbeddingModel embeddingModel;
    private final EmbeddingProperties props;

    OllamaEmbeddingService(EmbeddingModel embeddingModel, EmbeddingProperties props) {
        this.embeddingModel = embeddingModel;
        this.props = props;
        log.info("Ollama embeddings: model {}, query prefix \"{}\", passage prefix \"{}\"",
                props.model(), props.queryPrefix(), props.passagePrefix());
    }

    @Override
    public String modelName() {
        return EmbeddingService.canonicalModelName(props.model());
    }

    @Override
    public float[] embed(String text, EmbeddingKind kind) {
        Assert.hasText(text, "text to embed must not be blank");
        Assert.notNull(kind, "embedding kind must not be null");

        String prefixed = prefix(text, kind);
        return checked(embeddingModel.embed(prefixed));
    }

    /** One request for the whole batch. */
    @Override
    public List<float[]> embedAll(List<String> texts, EmbeddingKind kind) {
        Assert.notNull(kind, "embedding kind must not be null");
        if (texts.isEmpty()) return List.of();
        List<String> prefixed = new ArrayList<>(texts.size());
        for (String text : texts) {
            Assert.hasText(text, "text to embed must not be blank");
            prefixed.add(prefix(text, kind));
        }
        List<float[]> vectors = embeddingModel.embed(prefixed);
        if (vectors.size() != texts.size()) {
            throw new IllegalStateException("Embedding model '%s' returned %d vectors for %d texts"
                    .formatted(props.model(), vectors.size(), texts.size()));
        }
        return vectors.stream().map(this::checked).toList();
    }

    private String prefix(String text, EmbeddingKind kind) {
        return switch (kind) {
            case QUERY -> props.queryPrefix() + text;
            case PASSAGE -> props.passagePrefix() + text;
        };
    }

    private float[] checked(float[] vector) {
        if (vector.length != props.dimensions()) {
            throw new IllegalStateException(
                    "Embedding model '%s' returned %d dimensions, expected %d".formatted(props.model(), vector.length, props.dimensions()));
        }
        return vector;
    }
}
