package com.orbit;

import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The structure of application.yaml. A key that slips under the wrong parent
 * goes unnoticed at run time — the code falls back to its default, and the
 * environment variable that should override it does nothing.
 */
class ApplicationYamlTest {

    @Test
    void everyTunableSitsUnderTheKeyTheCodeReads() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yaml"));
        Properties properties = yaml.getObject();

        assertThat(properties).containsKeys(
                "orbit.search.candidate-depth",
                "orbit.search.rerank-depth",
                "orbit.search.rerank-passages",
                "orbit.search.rerank-passage-chars",
                "orbit.search.reranker-threshold",
                "orbit.search.inference-budget",
                "orbit.search.rate-limit.searches-per-minute",
                "orbit.search.rate-limit.rationales-per-minute",
                "orbit.ingestion.jobs.enabled",
                "orbit.tags.canonical-similarity",
                "orbit.tags.sync-on-startup",
                "orbit.requests.poll-interval",
                "orbit.requests.urgent-escalate-after",
                "orbit.requests.soon-remind-after",
                "orbit.requests.deadline-wave-before",
                "orbit.embedding.provider",
                "orbit.embedding.query-prefix",
                "orbit.embedding.passage-prefix",
                "orbit.embedding.reembed-before",
                "orbit.openrouter.api-key",
                "orbit.openrouter.data-collection",
                "orbit.openrouter.chat.model",
                "orbit.openrouter.embedding.model",
                "orbit.openrouter.embedding.query-input-type",
                "orbit.openrouter.reranker.model",
                "orbit.huggingface.token");
        assertThat(properties).doesNotContainKeys("orbit.tags.poll-interval", "orbit.tags.urgent-escalate-after");
    }
}
