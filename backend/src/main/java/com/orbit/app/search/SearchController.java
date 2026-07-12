package com.orbit.app.search;

import com.orbit.app.search.dto.SearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST endpoint for hybrid search. Public — no authentication required
 * (spec §4: "sofortiges semantisches Matching ... ohne Konto").
 * <p>
 * POST /api/v1/search with body:
 * <pre>{"query": "Python web framework", "matchCount": 10}</pre>
 * Returns a {@link SearchResponse} with ranked entities, evidence chunks, and a
 * {@code sufficient} quality flag. When {@code sufficient} is false, the results
 * list is empty and the frontend should prompt the user to create an Anfrage.
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
    public ResponseEntity<SearchResponse> search(@RequestBody Map<String, Object> body) {
        String query = (String) body.getOrDefault("query", "");
        int matchCount = body.containsKey("matchCount")
                ? ((Number) body.get("matchCount")).intValue()
                : 10;

        if (query.isBlank()) {
            return ResponseEntity.ok(new SearchResponse("", false, List.of()));
        }

        log.info("Search request: query=\"{}\", matchCount={}", query, matchCount);
        return ResponseEntity.ok(searchService.search(query, matchCount));
    }
}