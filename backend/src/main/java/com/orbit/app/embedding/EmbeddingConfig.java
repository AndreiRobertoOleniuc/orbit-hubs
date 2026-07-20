package com.orbit.app.embedding;

import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(EmbeddingProperties.class)
class EmbeddingConfig {

    @Bean
    @ConditionalOnProperty(name = "orbit.embedding.provider", havingValue = "ollama", matchIfMissing = true)
    OllamaApi embeddingOllamaApi(EmbeddingProperties props) {
        return OllamaApi.builder().baseUrl(props.baseUrl()).build();
    }

    @Bean
    @ConditionalOnProperty(name = "orbit.embedding.provider", havingValue = "ollama", matchIfMissing = true)
    OllamaEmbeddingModel embeddingModel(OllamaApi embeddingOllamaApi, EmbeddingProperties props) {
        return OllamaEmbeddingModel.builder()
                .ollamaApi(embeddingOllamaApi)
                .defaultOptions(OllamaEmbeddingOptions.builder().model(props.model()).build())
                .build();
    }
}
