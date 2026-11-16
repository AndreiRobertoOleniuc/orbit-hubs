package com.orbit.app.embedding;

import java.util.ArrayList;
import java.util.List;

/**
 * Published capability for turning text into a shared 1024-dimensional vector.
 */
public interface EmbeddingService {

    float[] embed(String text, EmbeddingKind kind);

    /**
     * The name stored with every vector this service produces
     * ({@code embedding_model}). Vectors of different names are not comparable:
     * a chunk, tag or profile document carrying another name is re-embedded.
     */
    String modelName();

    /**
     * One name for one model, whoever serves it: multilingual-e5-large gives the
     * same vectors from Ollama, Hugging Face and OpenRouter (cosine 1.000,
     * measured 2026-11-16), so its provider-specific ids share a stored name and
     * a change of provider re-embeds nothing. Any other id is its own name.
     */
    static String canonicalModelName(String providerModelId) {
        return providerModelId.toLowerCase(java.util.Locale.ROOT).contains("multilingual-e5-large")
                ? "multilingual-e5-large"
                : providerModelId;
    }

    /**
     * Embed several texts of the same kind, in input order. Fails as a whole:
     * either every text gets a vector or the call throws. Providers override
     * this with one request per batch; the default embeds one text at a time.
     */
    default List<float[]> embedAll(List<String> texts, EmbeddingKind kind) {
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (String text : texts) vectors.add(embed(text, kind));
        return vectors;
    }
}
