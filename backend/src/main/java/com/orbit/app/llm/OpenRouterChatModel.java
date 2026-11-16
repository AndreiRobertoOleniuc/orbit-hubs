package com.orbit.app.llm;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import com.orbit.app.inference.OpenRouterClient;
import com.orbit.app.inference.OpenRouterProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The LLM behind every prompt in {@link LlmSummaryService}: a chat completion
 * from OpenRouter ({@code orbit.openrouter.chat.*}).
 * <p>
 * Model, temperature and token limit come from configuration; a prompt's own
 * options override them (the ranking-relevant prompts ask for temperature 0).
 * The configured fallback models go along with every request: a single model
 * on OpenRouter can be rate limited for all its customers at once (a shared
 * upstream quota), and OpenRouter then answers with the next model itself.
 * Plain text in, plain text out — no tools, no streaming.
 */
@Component
class OpenRouterChatModel implements ChatModel {

    private static final Logger log = LoggerFactory.getLogger(OpenRouterChatModel.class);

    private final OpenRouterClient client;
    private final OpenRouterProperties.Chat defaults;
    private final JsonMapper objectMapper;

    OpenRouterChatModel(OpenRouterClient client, OpenRouterProperties props, JsonMapper objectMapper) {
        this.client = client;
        this.defaults = props.chat();
        this.objectMapper = objectMapper;
        log.info("LLM: OpenRouter model {} (fallbacks {}), temperature {}, at most {} output tokens",
                defaults.model(), defaults.fallbackModels(), defaults.temperature(), defaults.maxTokens());
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        ChatOptions options = prompt.getOptions();
        ObjectNode request = objectMapper.createObjectNode();
        request.put("model", options != null && options.getModel() != null ? options.getModel() : defaults.model());
        if (!defaults.fallbackModels().isEmpty()) {
            ArrayNode fallbacks = request.putArray("models");
            defaults.fallbackModels().forEach(fallbacks::add);
        }
        request.put("temperature",
                options != null && options.getTemperature() != null ? options.getTemperature() : defaults.temperature());
        request.put("max_tokens",
                options != null && options.getMaxTokens() != null ? options.getMaxTokens() : defaults.maxTokens());
        ArrayNode messages = request.putArray("messages");
        for (Message message : prompt.getInstructions()) {
            messages.addObject()
                    .put("role", message.getMessageType().getValue())
                    .put("content", message.getText());
        }

        JsonNode response = client.post("chat/completions", request, defaults.maxWait());
        JsonNode content = response.path("choices").path(0).path("message").path("content");
        if (!content.isString()) {
            throw new IllegalStateException("OpenRouter chat model returned no message text");
        }
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content.asString()))));
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return ChatOptions.builder()
                .model(defaults.model())
                .temperature(defaults.temperature())
                .maxTokens(defaults.maxTokens())
                .build();
    }
}
