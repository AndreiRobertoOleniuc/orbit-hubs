package com.orbit.app.search;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.orbit.app.inference.InferenceUnavailableException;

@Component
@ConditionalOnProperty(name = "orbit.search.reranker-provider", havingValue = "local", matchIfMissing = true)
class LocalCrossEncoderClient implements CrossEncoderClient {

    private final RestClient client;

    LocalCrossEncoderClient(@Value("${orbit.search.reranker-url:http://localhost:8001}") String rerankerUrl) {
        this.client = RestClient.builder().baseUrl(rerankerUrl).build();
    }

    @Override
    public List<Double> score(String query, List<String> documents) {
        try {
            RerankResponse response = client.post()
                    .uri("/rerank")
                    .body(new RerankRequest(query, documents))
                    .retrieve()
                    .body(RerankResponse.class);
            if (response == null || response.results() == null || response.results().size() != documents.size()) {
                throw new IllegalStateException("Local reranker returned the wrong number of scores");
            }
            List<Double> scores = new ArrayList<>(java.util.Collections.nCopies(documents.size(), null));
            for (RerankScore result : response.results()) {
                if (result.index() < 0 || result.index() >= documents.size() || scores.get(result.index()) != null) {
                    throw new IllegalStateException("Local reranker returned an invalid or duplicate index");
                }
                scores.set(result.index(), result.score());
            }
            return List.copyOf(scores);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new InferenceUnavailableException("Local cross-encoder reranker is unavailable", ex);
        }
    }

    record RerankRequest(String query, List<String> documents) {
    }

    record RerankScore(int index, double score) {
    }

    record RerankResponse(List<RerankScore> results) {
    }
}
