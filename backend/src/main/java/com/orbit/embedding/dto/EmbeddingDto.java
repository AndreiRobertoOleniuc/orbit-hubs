package com.orbit.embedding.dto;

/**
 * Placeholder DTO for embedding results shared with other modules.
 */
public record EmbeddingDto(float[] vector, EmbeddingRequestKind kind) {

    public enum EmbeddingRequestKind {
        QUERY,
        PASSAGE
    }

}
