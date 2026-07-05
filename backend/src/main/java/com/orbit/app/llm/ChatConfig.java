package com.orbit.app.llm;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Configures the Ollama chat model for LLM-based summarization.
 * Uses the same Ollama instance as the embedding model, but with a
 * chat-capable model (e.g. gpt-oss:20b-cloud).
 * <p>
 * Sets a generous read timeout (120s) because cloud models can be slow,
 * especially for longer READMEs.
 */
@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class ChatConfig {

    @Bean
    OllamaApi chatOllamaApi(LlmProperties props) {
        // JDK HttpClient with generous timeouts for LLM calls
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(120));

        RestClient.Builder restClientBuilder = RestClient.builder()
                .requestFactory(requestFactory);

        return OllamaApi.builder()
                .baseUrl(props.baseUrl())
                .restClientBuilder(restClientBuilder)
                .build();
    }

    @Bean
    OllamaChatModel chatModel(OllamaApi chatOllamaApi, LlmProperties props) {
        return OllamaChatModel.builder()
                .ollamaApi(chatOllamaApi)
                .defaultOptions(OllamaChatOptions.builder()
                        .model(props.chatModel())
                        .temperature(props.temperature())
                        .numPredict(props.maxTokens())
                        .build())
                .build();
    }
}