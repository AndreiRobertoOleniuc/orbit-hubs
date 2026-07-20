package com.orbit.app.search;

import com.orbit.domain.entity.DiscoveryEntity;

/** Input to cross-encoder reranking with retrieval context retained. */
public record RerankCandidate(
        DiscoveryEntity entity,
        double baseScore,
        String topEvidence
) {
}
