package com.orbit.app.search;

import java.util.List;

import com.orbit.domain.entity.DiscoveryEntity;

/**
 * Input to cross-encoder reranking: a candidate and the passages that may show
 * it is relevant — its profile card and its best evidence chunks. The
 * candidate scores as its best passage.
 */
public record RerankCandidate(
        DiscoveryEntity entity,
        double baseScore,
        List<RerankPassage> passages
) {
    public RerankCandidate {
        passages = List.copyOf(passages);
    }
}
