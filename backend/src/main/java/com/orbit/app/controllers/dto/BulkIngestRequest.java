package com.orbit.app.controllers.dto;

import java.util.List;
import java.util.Map;

/**
 * Test-only bulk ingest request. Simulates the onboarding flow: provide a
 * name, entity type, and a list of sources (same context fields as the real
 * ingestion API). The bulk service builds SourceRefs and runs them through
 * the real IngestionOrchestrator — adapters fetch, LLM summarizes, chunks
 * get embedded, enrichment runs.
 *
 * @param profiles the profiles to ingest
 */
public record BulkIngestRequest(
        List<BulkProfile> profiles
) {
    /**
     * One profile in the bulk request. Gets a unique fake ownerId so all its
     * sources converge on one entity (same as real onboarding).
     *
     * @param name    display name (used for entity creation on first source)
     * @param type    "person" or "organization"
     * @param sources list of sources — same context as IngestionRequest
     */
    public record BulkProfile(
            String name,
            String type,
            List<String> hubs,
            List<BulkSource> sources
    ) {}

    /**
     * One source for a profile. The context map uses the same keys as the
     * real ingestion API:
     * <ul>
     *   <li>github: {"github_login": "username"}</li>
     *   <li>orcid_pub: {"orcid": "0000-..."} or {"openalex_author_id": "..."}</li>
     *   <li>zefix: {"company_name": "Apiax AG"} or {"zefix_uid": "CHE-..."}</li>
     *   <li>personal_site: {"url": "https://..."}</li>
     *   <li>company_site: {"url": "https://..."}</li>
     *   <li>manual_text: {"text": "some text"}</li>
     * </ul>
     *
     * @param sourceType github | orcid_pub | zefix | personal_site | company_site | manual_text
     * @param context    source-specific parameters (same keys as IngestionRequest.context)
     */
    public record BulkSource(
            String sourceType,
            Map<String, String> context
    ) {}
}