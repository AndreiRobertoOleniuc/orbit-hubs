package com.orbit.app.embedding;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "orbit.embedding")
public record EmbeddingProperties(

        @DefaultValue("http://localhost:11434") String baseUrl,

        @DefaultValue("jeffh/intfloat-multilingual-e5-large:f16") String model,

        @DefaultValue("1024") int dimensions,

        @DefaultValue("query: ") String queryPrefix,

        @DefaultValue("passage: ") String passagePrefix) {
}
