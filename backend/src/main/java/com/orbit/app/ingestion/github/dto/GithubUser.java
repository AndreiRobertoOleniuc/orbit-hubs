package com.orbit.app.ingestion.github.dto;

/**
 * GitHub user profile data fetched from the REST API.
 * Used for entity creation during ingestion.
 */
public record GithubUser(
        String login,
        String displayName,
        String bio,
        String company,
        String location,
        String blog,
        int publicRepos,
        int followers
) {}