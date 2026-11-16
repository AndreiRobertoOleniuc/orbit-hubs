package com.orbit.app.llm;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.orbit.app.llm.dto.SearchQueryRewrite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmSummaryServiceTest {

    private static final String QUERY = "Wir suchen Expertise für sichere vernetzte Robotik.";

    private ChatModel chatModel;
    private LlmSummaryService service;
    private final List<Prompt> prompts = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        chatModel = mock(ChatModel.class);
        service = new LlmSummaryService(chatModel);
    }

    @Test
    void searchQueryRewriteRunsBothPromptsAtTemperatureZeroAndReturnsOneLineForms() {
        answer("We are looking for expertise in secure\nconnected robotics.", "secure connected robotics,\n expert");

        SearchQueryRewrite rewrite = service.rewriteSearchQuery(QUERY);

        assertThat(rewrite.english()).isEqualTo("We are looking for expertise in secure connected robotics.");
        assertThat(rewrite.searchTerms()).isEqualTo("secure connected robotics, expert");
        assertThat(prompts).hasSize(2);
        // The query has its own translation prompt, not the ingestion one that
        // returned German queries unchanged.
        assertThat(prompts).anySatisfy(prompt ->
                assertThat(prompt.getContents()).contains("Translate the search query below into English"));
        assertThat(prompts).noneSatisfy(prompt ->
                assertThat(prompt.getContents()).contains("Keep the same structure (headings, paragraphs)"));
        assertThat(prompts).allSatisfy(prompt -> {
            assertThat(prompt.getOptions().getTemperature()).isEqualTo(0.0);
            assertThat(prompt.getContents()).contains(QUERY);
        });
    }

    @Test
    void searchQueryRewriteIsNullWhenEitherPromptGivesNoAnswer() {
        answer("We are looking for expertise in secure connected robotics.", "  ");

        assertThat(service.rewriteSearchQuery(QUERY)).isNull();
        assertThat(service.rewriteSearchQuery("  ")).isNull();
    }

    @Test
    void ingestionTranslationKeepsTheConfiguredDefaultsAndFallsBackToTheOriginal() {
        answer("Professional experience in embedded systems.", "unused");
        assertThat(service.translateToEnglish("Berufserfahrung in eingebetteten Systemen."))
                .isEqualTo("Professional experience in embedded systems.");
        assertThat(prompts).singleElement().satisfies(prompt -> assertThat(prompt.getOptions()).isNull());

        answer("", "unused");
        assertThat(service.translateToEnglish("Berufserfahrung in eingebetteten Systemen."))
                .isEqualTo("Berufserfahrung in eingebetteten Systemen.");
    }

    /** Answer the translation prompt with {@code translation} and the condense prompt with {@code terms}. */
    private void answer(String translation, String terms) {
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            prompts.add(prompt);
            boolean condense = prompt.getContents().contains("extract the key search terms");
            return new ChatResponse(List.of(new Generation(new AssistantMessage(condense ? terms : translation))));
        });
    }
}
