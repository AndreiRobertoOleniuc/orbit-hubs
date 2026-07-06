package com.orbit.app.ingestion.github.dto;

import java.util.List;

/**
 * GitHub repository metadata used during ingestion.
 * Each repo becomes one source in the entity → source → chunk hierarchy.
 */
public record GithubRepo(
        String ownerLogin,
        String name,
        String description,
        String language,
        List<String> topics,
        int stargazersCount,
        String pushedAt
) {}