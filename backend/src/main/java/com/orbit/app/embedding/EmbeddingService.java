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
