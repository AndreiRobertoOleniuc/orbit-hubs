package com.orbit.app.controllers;

import java.util.List;

import com.orbit.app.controllers.dto.TagRefDto;
import com.orbit.domain.repository.TagRepository;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Autocomplete over the controlled tag vocabulary. Backs the "add tag" inputs
 * (subscriptions, Kompetenzen) so users converge on existing tags rather than
 * minting near-duplicates. Returns up to 10 label-matching tags; an empty query
 * returns nothing.
 */
@RestController
class TagController {

    private final TagRepository tags;

    TagController(TagRepository tags) {
        this.tags = tags;
    }

    @GetMapping("/api/v1/tags")
    List<TagRefDto> search(@RequestParam(name = "q", required = false, defaultValue = "") String q) {
        String query = q.trim();
        if (query.isEmpty()) {
            return List.of();
        }
        return tags.findTop10ByLabelContainingIgnoreCaseOrderByLabelAsc(query).stream()
                .map(t -> new TagRefDto(t.getId(), t.getSlug(), t.getLabel()))
                .toList();
    }
}
