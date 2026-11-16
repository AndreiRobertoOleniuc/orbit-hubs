package com.orbit.app.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.orbit.app.inference.InferenceUnavailableException;
import com.orbit.domain.entity.DiscoveryEntity;

class CrossEncoderRerankerTest {

    @Test
    void aCandidateScoresAsItsBestPassageAndRemembersWhichOneThatWas() {
        List<List<String>> calls = new ArrayList<>();
        CrossEncoderReranker reranker = new CrossEncoderReranker((query, documents) -> {
            calls.add(documents);
            return List.of(0.2, 0.7, 0.1, 0.05, 0.9);
        }, 0.55);

        List<RerankResult> scored = reranker.score("security expert", List.of(
                candidate("A", "a1", "a2", "a3"), candidate("B", "b1"), candidate("C", "c1")));

        assertThat(calls).singleElement().asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .containsExactly("a1", "a2", "a3", "b1", "c1");   // one provider call for all passages
        assertThat(scored).extracting(RerankResult::rerankerScore).containsExactly(0.7, 0.05, 0.9);
        assertThat(scored).extracting(result -> result.bestPassage().text()).containsExactly("a2", "b1", "c1");
    }

    @Test
    void keepRelevantFiltersAndSortsByScore() {
        CrossEncoderReranker reranker = new CrossEncoderReranker((query, documents) -> List.of(0.7, 0.2, 0.9), 0.55);

        List<RerankResult> results = reranker.rerank("security expert",
                List.of(candidate("A", "a"), candidate("B", "b"), candidate("C", "c")));

        assertThat(results).extracting(result -> result.candidate().entity().getDisplayName())
                .containsExactly("C", "A");
        assertThat(reranker.relevanceThreshold()).isEqualTo(0.55);
    }

    @Test
    void aCandidateWithoutPassagesScoresZeroWithoutAProviderCall() {
        CrossEncoderReranker reranker = new CrossEncoderReranker((query, documents) -> {
            throw new AssertionError("no passages, no call");
        }, 0.55);

        assertThat(reranker.score("security", List.of(candidate("A")))).singleElement().satisfies(result -> {
            assertThat(result.rerankerScore()).isZero();
            assertThat(result.bestPassage()).isNull();
        });
    }

    @Test
    void providerFailurePropagatesInsteadOfReturningUnrerankedResults() {
        CrossEncoderReranker reranker = new CrossEncoderReranker((query, documents) -> {
            throw new InferenceUnavailableException("down");
        }, 0.55);

        assertThatThrownBy(() -> reranker.rerank("security", List.of(candidate("A", "a"))))
                .isInstanceOf(InferenceUnavailableException.class);
    }

    @Test
    void anEvidencePassageNamesItsOwnerAndSourceAndIsCapped() {
        DiscoveryEntity entity = new DiscoveryEntity("person", "Ada Muster", "Robotics researcher", null);

        RerankPassage passage = CrossEncoderReranker.evidencePassage(entity, "orcid_pub", "«Safe robots» (2021)", "Abstract.");

        assertThat(passage.label()).isEqualTo("«Safe robots» (2021)");
        assertThat(passage.text()).isEqualTo("Ada Muster — Robotics researcher\nPublication: «Safe robots» (2021)\n\nAbstract.");
        assertThat(CrossEncoderReranker.evidencePassage(entity, "unknown", null, "x".repeat(5000)).text())
                .startsWith("Ada Muster — Robotics researcher\nSource\n\nx")
                .hasSize(CrossEncoderReranker.MAX_PASSAGE_CHARS);
    }

    @Test
    void theCardHoldsNameHeadlineTagsLeadingSummarySentencesAndTheStartOfTheBestChunk() {
        DiscoveryEntity entity = new DiscoveryEntity("person", "Ada", "Researcher", null);
        entity.setSummary("First sentence. Second sentence. Third sentence. Fourth sentence.");

        RerankPassage card = CrossEncoderReranker.cardPassage(entity, List.of("Tag A", "Tag B"), "e".repeat(800));

        assertThat(card.label()).isEqualTo(RerankPassage.CARD_LABEL);
        assertThat(card.text()).isEqualTo("Ada: Researcher. Expertise: Tag A, Tag B. "
                + "First sentence. Second sentence. Third sentence. Matching evidence: " + "e".repeat(500));
    }

    private static RerankCandidate candidate(String name, String... passages) {
        DiscoveryEntity entity = new DiscoveryEntity("person", name, "Researcher", null);
        return new RerankCandidate(entity, 0.5,
                java.util.Arrays.stream(passages).map(text -> new RerankPassage(text, text)).toList());
    }
}
