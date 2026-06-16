package com.orbit.discovery.internal;

import com.orbit.embedding.EmbeddingService;
import com.orbit.profile.ProfileQuery;
import com.orbit.request.RequestQuery;

/**
 * Skeleton placeholder that wires the allowed incoming dependencies of the
 * discovery module: it reads profiles and requests and uses the shared
 * embedding capability, but never reaches into their internal packages.
 */
class DiscoveryPlaceholder {

    private final EmbeddingService embedding;
    private final ProfileQuery profiles;
    private final RequestQuery requests;

    DiscoveryPlaceholder(EmbeddingService embedding, ProfileQuery profiles, RequestQuery requests) {
        this.embedding = embedding;
        this.profiles = profiles;
        this.requests = requests;
    }

    void todo() {
        throw new UnsupportedOperationException("not implemented");
    }

}
