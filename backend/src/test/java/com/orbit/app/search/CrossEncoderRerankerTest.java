package com.orbit.app.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.orbit.app.inference.InferenceUnavailableException;
import com.orbit.domain.entity.DiscoveryEntity;

class CrossEncoderRerankerTest {

    @Test
    void filtersAndSortsScoresFromInputOrder() {
        CrossEncoderReranker reranker = new CrossEncoderReranker((query, documents) -> List.of(0.7, 0.2, 0.9), 0.55);
        List<RerankCandidate> candidates = List.of(
                candidate("A"), candidate("B"), candidate("C"));

        List<RerankResult> results = reranker.rerank("security expert", candidates);

        assertThat(results).extracting(result -> result.candidate().entity().getDisplayName())
                .containsExactly("C", "A");
        assertThat(results).extracting(RerankResult::rerankerScore)
                .containsExactly(0.9, 0.7);
    }

    @Test
    void providerFailurePropagatesInsteadOfReturningUnrerankedResults() {
        CrossEncoderReranker reranker = new CrossEncoderReranker((query, documents) -> {
            throw new InferenceUnavailableException("down");
        }, 0.55);

        assertThatThrownBy(() -> reranker.rerank("security", List.of(candidate("A"))))
                .isInstanceOf(InferenceUnavailableException.class);
    }

    private static RerankCandidate candidate(String name) {
        DiscoveryEntity entity = new DiscoveryEntity("person", name, "Researcher", null);
        entity.setSummary("First sentence. Second sentence. Third sentence. Fourth sentence.");
        return new RerankCandidate(entity, 0.5, "evidence");
    }
}
