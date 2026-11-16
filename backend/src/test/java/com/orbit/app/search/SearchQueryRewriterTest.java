package com.orbit.app.search;

import com.orbit.app.llm.LlmSummaryService;
import com.orbit.app.llm.dto.SearchQueryRewrite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchQueryRewriterTest {

    private LlmSummaryService llm;
    private SearchQueryRewriter rewriter;

    @BeforeEach
    void setUp() {
        llm = mock(LlmSummaryService.class);
        rewriter = new SearchQueryRewriter(llm);
    }

    @Test
    void theSameRequestIsRewrittenOnceHoweverItIsSpacedOrCased() {
        SearchQueryRewrite rewrite = new SearchQueryRewrite("We are looking for a robotics expert", "robotics expert");
        when(llm.rewriteSearchQuery("Wir suchen Robotik-Expertise")).thenReturn(rewrite);

        assertThat(rewriter.rewrite("Wir suchen Robotik-Expertise")).isSameAs(rewrite);
        assertThat(rewriter.rewrite("  wir suchen   ROBOTIK-expertise ")).isSameAs(rewrite);

        verify(llm, times(1)).rewriteSearchQuery(anyString());
    }

    @Test
    void aFailedRewriteFallsBackToTheOriginalAndIsNotCached() {
        when(llm.rewriteSearchQuery("Wir suchen Robotik-Expertise")).thenReturn(null);

        SearchQueryRewrite fallback = rewriter.rewrite("Wir suchen Robotik-Expertise");
        rewriter.rewrite("Wir suchen Robotik-Expertise");

        assertThat(fallback.english()).isEqualTo("Wir suchen Robotik-Expertise");
        assertThat(fallback.searchTerms()).isEqualTo("Wir suchen Robotik-Expertise");
        verify(llm, times(2)).rewriteSearchQuery("Wir suchen Robotik-Expertise");   // asked again next time
    }

    @Test
    void veryShortRequestsAreUsedAsTheyAreWithoutAnLlmCall() {
        SearchQueryRewrite rewrite = rewriter.rewrite(" Java ");

        assertThat(rewrite.english()).isEqualTo("Java");
        assertThat(rewrite.searchTerms()).isEqualTo("Java");
        verify(llm, never()).rewriteSearchQuery(anyString());
    }
}
