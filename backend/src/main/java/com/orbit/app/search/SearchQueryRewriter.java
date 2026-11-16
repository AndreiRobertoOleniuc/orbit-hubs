package com.orbit.app.search;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.orbit.app.llm.LlmSummaryService;
import com.orbit.app.llm.dto.SearchQueryRewrite;

import org.springframework.stereotype.Service;

/**
 * Turns a raw search request into the two query forms the pipeline needs, and
 * makes that step repeatable: the same request always yields the same rewrite.
 * <p>
 * The rewrite is one temperature-0 LLM call, cached by the normalized request.
 * Without the cache an identical search could still be reworded differently and
 * come back in a different order. Failures are never cached — the search then
 * runs on the original text and the next attempt asks the LLM again.
 */
@Service
public class SearchQueryRewriter {

    /** Shorter requests are used as-is; there is nothing to condense. */
    private static final int MIN_REWRITE_LENGTH = 10;
    private static final int CACHE_SIZE = 1000;

    private final LlmSummaryService llm;
    private final Map<String, SearchQueryRewrite> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, SearchQueryRewrite> eldest) {
                    return size() > CACHE_SIZE;
                }
            });

    public SearchQueryRewriter(LlmSummaryService llm) {
        this.llm = llm;
    }

    /** The rewrite for {@code query}; falls back to the original text for both forms. */
    public SearchQueryRewrite rewrite(String query) {
        String original = query.trim();
        if (original.length() < MIN_REWRITE_LENGTH) {
            return new SearchQueryRewrite(original, original);
        }

        String key = original.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        SearchQueryRewrite cached = cache.get(key);
        if (cached != null) {
            return cached;
        }

        SearchQueryRewrite rewrite = llm.rewriteSearchQuery(original);
        if (rewrite == null) {
            return new SearchQueryRewrite(original, original);
        }
        cache.put(key, rewrite);
        return rewrite;
    }
}
