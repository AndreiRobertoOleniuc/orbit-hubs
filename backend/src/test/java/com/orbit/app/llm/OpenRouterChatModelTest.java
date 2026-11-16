package com.orbit.app.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import com.orbit.app.inference.OpenRouterClient;
import com.orbit.app.inference.OpenRouterProperties;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class OpenRouterChatModelTest {

    private static final Duration CHAT_WAIT = Duration.ofSeconds(20);

    private final JsonMapper objectMapper = JsonMapper.builder().build();
    private final OpenRouterClient client = mock(OpenRouterClient.class);
    private final OpenRouterChatModel model = new OpenRouterChatModel(client,
            new OpenRouterProperties("sk-test", "https://openrouter.test/api/v1", "deny",
                    Duration.ofMinutes(2), Duration.ofSeconds(5), Duration.ofSeconds(30),
                    new OpenRouterProperties.Chat("google/gemini-test", List.of(), 0.3, 500, CHAT_WAIT),
                    new OpenRouterProperties.Embedding("embed/model", "", ""),
                    new OpenRouterProperties.Reranker("rerank/model")),
            objectMapper);

    @Test
    void aPromptIsSentAsOneUserMessageWithTheConfiguredModelAndLimits() {
        answer("{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"A summary.\"}}]}");

        String text = model.call(new Prompt("Summarise this.")).getResult().getOutput().getText();

        assertThat(text).isEqualTo("A summary.");
        ObjectNode request = sentRequest();
        assertThat(request.get("model").asString()).isEqualTo("google/gemini-test");
        assertThat(request.get("temperature").asDouble()).isEqualTo(0.3);
        assertThat(request.get("max_tokens").asInt()).isEqualTo(500);
        assertThat(request.get("messages")).hasSize(1);
        assertThat(request.get("messages").get(0).get("role").asString()).isEqualTo("user");
        assertThat(request.get("messages").get(0).get("content").asString()).isEqualTo("Summarise this.");
    }

    @Test
    void aPromptsOwnOptionsOverrideTheDefaults() {
        answer("{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}");

        model.call(new Prompt("Translate.", ChatOptions.builder().temperature(0.0).build()));

        ObjectNode request = sentRequest();
        assertThat(request.get("temperature").asDouble()).isZero();
        assertThat(request.get("model").asString()).isEqualTo("google/gemini-test");
        assertThat(request.get("max_tokens").asInt()).isEqualTo(500);
    }

    @Test
    void fallbackModelsGoAlongWithEveryRequest() {
        OpenRouterChatModel withFallbacks = new OpenRouterChatModel(client,
                new OpenRouterProperties("sk-test", "https://openrouter.test/api/v1", "deny",
                        Duration.ofMinutes(2), Duration.ofSeconds(5), Duration.ofSeconds(30),
                        new OpenRouterProperties.Chat("first/model", List.of("second/model", "third/model"),
                                0.3, 500, CHAT_WAIT),
                        new OpenRouterProperties.Embedding("embed/model", "", ""),
                        new OpenRouterProperties.Reranker("rerank/model")),
                objectMapper);
        answer("{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}");

        withFallbacks.call(new Prompt("Summarise this."));

        ObjectNode request = sentRequest();
        assertThat(request.get("model").asString()).isEqualTo("first/model");
        assertThat(request.get("models")).hasSize(2);
        assertThat(request.get("models").get(0).asString()).isEqualTo("second/model");
    }

    @Test
    void withoutFallbackModelsOnlyTheModelIsNamed() {
        answer("{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}");

        model.call(new Prompt("Summarise this."));

        assertThat(sentRequest().has("models")).isFalse();
    }

    @Test
    void anAnswerWithoutTextIsAnError() {
        answer("{\"choices\":[]}");

        assertThatThrownBy(() -> model.call(new Prompt("Summarise this.")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("no message text");
    }

    private void answer(String json) {
        when(client.post(eq("chat/completions"), any(), eq(CHAT_WAIT)))
                .thenAnswer(invocation -> objectMapper.readTree(json));
    }

    private ObjectNode sentRequest() {
        ArgumentCaptor<ObjectNode> request = ArgumentCaptor.forClass(ObjectNode.class);
        verify(client).post(eq("chat/completions"), request.capture(), eq(CHAT_WAIT));
        return request.getValue();
    }
}
