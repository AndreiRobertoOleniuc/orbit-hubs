package com.orbit.app.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.orbit.app.inference.HfInferenceClient;
import com.orbit.app.inference.HfInferenceProperties;
import tools.jackson.databind.json.JsonMapper;

class HfCrossEncoderClientTest {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final HfCrossEncoderClient client = new HfCrossEncoderClient(
            mock(HfInferenceClient.class),
            new HfInferenceProperties("hf_test", "https://example.test", "embed", "rerank",
                    Duration.ofMinutes(2), Duration.ofSeconds(5), Duration.ofSeconds(30)),
            mapper);

    @Test
    void parsesObservedSingleBatchShape() throws Exception {
        assertThat(client.parseScores(mapper.readTree("""
                [[{"label":"LABEL_0","score":0.8},{"label":"LABEL_0","score":0.1}]]
                """), 2)).containsExactly(sigmoid(0.8), sigmoid(0.1));
    }

    @Test
    void parsesPerPairNestedAndFlatShapes() throws Exception {
        assertThat(client.parseScores(mapper.readTree("""
                [[{"score":0.8}],[{"score":0.1}]]
                """), 2)).containsExactly(sigmoid(0.8), sigmoid(0.1));
        assertThat(client.parseScores(mapper.readTree("""
                [{"score":0.8},{"score":0.1}]
                """), 2)).containsExactly(sigmoid(0.8), sigmoid(0.1));
    }

    @Test
    void rejectsCountMismatchAndMalformedScore() throws Exception {
        assertThatThrownBy(() -> client.parseScores(mapper.readTree("[{\"score\":0.8}]"), 2))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> client.parseScores(mapper.readTree("[{\"label\":\"LABEL_0\"}]"), 1))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("score");
    }

    private static double sigmoid(double value) {
        return 1.0 / (1.0 + Math.exp(-value));
    }
}
