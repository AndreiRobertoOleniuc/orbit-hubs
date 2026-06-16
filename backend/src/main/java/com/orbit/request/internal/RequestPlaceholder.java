package com.orbit.request.internal;

import com.orbit.embedding.EmbeddingService;

/**
 * Skeleton placeholder that wires the single allowed outgoing dependency of the
 * request module: it may depend on the published {@link EmbeddingService}.
 */
class RequestPlaceholder {

    private final EmbeddingService embedding;

    RequestPlaceholder(EmbeddingService embedding) {
        this.embedding = embedding;
    }

    void todo() {
        throw new UnsupportedOperationException("not implemented");
    }

}
