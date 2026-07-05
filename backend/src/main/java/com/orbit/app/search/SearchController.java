package com.orbit.app.search;

import java.util.List;
import java.util.Map;

import com.orbit.app.search.dto.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint for hybrid search.
 * <p>
 * POST /api/v1/search with body:
 * <pre>{"query": "Python web framework", "matchCount": 5}</pre>
 * Returns ranked entities with evidence chunks and RRF scores.
 */
@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private static final Logger log = LoggerFactory.getLogger(SearchController.class);

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @PostMapping
    public List<SearchResult> search(@RequestBody Map<String, Object> body) {
        String query = (String) body.getOrDefault("query", "");
        int matchCount = body.containsKey("matchCount")
                ? ((Number) body.get("matchCount")).intValue()
                : 5;

        if (query.isBlank()) {
            return List.of();
        }

        log.info("Search request: query=\"{}\", matchCount={}", query, matchCount);
        return searchService.search(query, matchCount);
    }
}