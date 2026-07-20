package com.orbit.app.inference;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "orbit.huggingface")
public record HfInferenceProperties(
        @DefaultValue("") String token,
        @DefaultValue("https://router.huggingface.co/hf-inference/models") String baseUrl,
        @DefaultValue("intfloat/multilingual-e5-large") String embeddingModel,
        @DefaultValue("BAAI/bge-reranker-v2-m3") String rerankerModel,
        @DefaultValue("PT2M") Duration maxWait,
        @DefaultValue("PT5S") Duration connectTimeout,
        @DefaultValue("PT30S") Duration requestTimeout) {

    public String requiredToken() {
        if (!StringUtils.hasText(token)) {
            throw new IllegalStateException("HF_TOKEN must be set when a Hugging Face inference provider is enabled");
        }
        return token;
    }
}
