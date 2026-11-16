package com.orbit.app.search;

import com.orbit.app.search.dto.SearchRationale;
import com.orbit.app.search.dto.SearchRationaleRequest;
import com.orbit.app.search.dto.SearchRequest;
import com.orbit.app.search.dto.SearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Thin REST endpoint for the discovery search. Public — no authentication
 * required (spec §4: semantic matching "ohne Konto"). All logic lives in
 * {@link SearchService}.
 * <p>
 * POST /api/v1/search with body:
 * <pre>{"query": "embedded firmware", "scope": {"kind": "hub", "hubId": 1},
 * "wantFrom": "2026-08-01", "wantTo": "2026-10-31", "wantPensumPct": 60}</pre>
 * POST /api/v1/search/rationale with {@code {"query": "…", "entityId": 42}}
 * returns the grounded rationale of one result; each result card asks for its own.
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
    public ResponseEntity<SearchResponse> search(@RequestBody SearchRequest request) {
        if (request == null || request.query() == null || request.query().isBlank()) {
            return ResponseEntity.ok(new SearchResponse("", false, List.of()));
        }
        log.info("Search request: query=\"{}\"", request.query());
        return ResponseEntity.ok(searchService.search(request));
    }

    @PostMapping("/rationale")
    public ResponseEntity<SearchRationale> rationale(@RequestBody SearchRationaleRequest request) {
        SearchRationale rationale = searchService.explain(request);
        return rationale != null ? ResponseEntity.ok(rationale) : ResponseEntity.notFound().build();
    }
}
