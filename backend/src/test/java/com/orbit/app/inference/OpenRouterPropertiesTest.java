package com.orbit.app.inference;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The models production runs come from application.yaml alone; a missing one stops the start. */
class OpenRouterPropertiesTest {

    @Test
    void applicationYamlSelectsTheProductionModels() throws Exception {
        List<PropertySource<?>> yaml = new YamlPropertySourceLoader()
                .load("application.yaml", new ClassPathResource("application.yaml"));
        OpenRouterProperties props = new Binder(ConfigurationPropertySources.from(yaml))
                .bind("orbit.openrouter", OpenRouterProperties.class).get();

        assertThat(props.dataCollection()).isEqualTo("deny");
        assertThat(props.chat().model()).isEqualTo("google/gemini-2.5-flash-lite");
        assertThat(props.chat().fallbackModels())
                .containsExactly("google/gemini-3.1-flash-lite", "anthropic/claude-haiku-5.5");
        assertThat(props.chat().temperature()).isEqualTo(0.3);
        assertThat(props.chat().maxTokens()).isEqualTo(500);
        assertThat(props.embedding().model()).isEqualTo("mongodb/voyage-4-lite");
        assertThat(props.embedding().dimensions()).isEqualTo(1024);
        assertThat(props.embedding().queryInputType()).isEqualTo("search_query");
        assertThat(props.embedding().passageInputType()).isEqualTo("search_document");
        assertThat(props.embedding().queryPrefix()).isEmpty();
        assertThat(props.reranker().model()).isEqualTo("cohere/rerank-5-fast");
    }

    @Test
    void aMissingModelChoiceStopsTheStartInsteadOfFallingBackToADefault() {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of(
                "orbit.openrouter.data-collection", "deny",
                "orbit.openrouter.embedding.model", "some/model",
                "orbit.openrouter.embedding.dimensions", "1024",
                "orbit.openrouter.reranker.model", "some/reranker")));

        assertThatThrownBy(() -> binder.bind("orbit.openrouter", OpenRouterProperties.class))
                .isInstanceOf(BindException.class)
                .rootCause().hasMessageContaining("orbit.openrouter.chat.model must be set");
    }
}
