package com.orbit.embedding;

/**
 * Published capability for turning text into a shared 1024-dimensional vector.
 */
public interface EmbeddingService {

    float[] embed(String text, EmbeddingKind kind);

}
