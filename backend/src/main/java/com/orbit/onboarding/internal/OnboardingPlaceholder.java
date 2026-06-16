package com.orbit.onboarding.internal;

import com.orbit.embedding.EmbeddingService;
import com.orbit.profile.ProfileQuery;

/**
 * Skeleton placeholder that wires the allowed outgoing dependencies of the
 * onboarding module: it may write into profiles and use the shared embedding
 * capability, but only via published interfaces.
 */
class OnboardingPlaceholder {

    private final EmbeddingService embedding;
    private final ProfileQuery profiles;

    OnboardingPlaceholder(EmbeddingService embedding, ProfileQuery profiles) {
        this.embedding = embedding;
        this.profiles = profiles;
    }

    void todo() {
        throw new UnsupportedOperationException("not implemented");
    }

}
