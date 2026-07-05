package com.orbit.app.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for the LLM chat model used for summarization.
 * Points to the same Ollama instance as the embedding model.
 */
@ConfigurationProperties(prefix = "orbit.llm")
public record LlmProperties(

        @DefaultValue("http://localhost:11434") String baseUrl,

        @DefaultValue("gpt-oss:20b-cloud") String chatModel,

        @DefaultValue("0.3") double temperature,

        @DefaultValue("500") int maxTokens
) {
}