package com.orbit.app.ingestion.dto;

import java.util.Map;

/**
 * A source reference — the input to the ingestion pipeline.
 * <p>
 * Carries enough context for an adapter to fetch the content and for the
 * orchestrator to track provenance/consent.
 */
public record SourceRef(
        String url,                    // source_url (idempotency key, unique per source)
        String sourceType,             // 'github' | 'orcid_pub' | 'linkedin_pdf' | 'personal_site'
        String provenance,             // 'harvested' | 'user_supplied' | 'self_declared'
        String consentBasis,           // 'public_data' | 'user_consent' | 'self_declared'
        Map<String, String> context  // source-specific params (e.g. {"github_login": "torvalds"})
) {
}