package com.orbit.profile.internal;

import com.orbit.embedding.EmbeddingService;

/**
 * Skeleton placeholder that wires the single allowed outgoing dependency of the
 * profile module: it may depend on the published {@link EmbeddingService}.
 */
class ProfilePlaceholder {

    private final EmbeddingService embedding;

    ProfilePlaceholder(EmbeddingService embedding) {
        this.embedding = embedding;
    }

    void todo() {
        throw new UnsupportedOperationException("not implemented");
    }

}
