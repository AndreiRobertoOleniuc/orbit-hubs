package com.orbit.app.ingestion.dto;

import java.util.Map;

/**
 * A source reference — the input to the ingestion pipeline.
 * <p>
 * Carries enough context for an adapter to fetch the content and for the
 * orchestrator to track provenance/consent. The {@code ownerId} links the
 * resulting entity to the authenticated user who initiated the ingestion;
 * it is {@code null} for admin/system ingestions.
 */
public record SourceRef(
        String url,                    // source_url (idempotency key, unique per source)
        String sourceType,             // 'github' | 'orcid_pub' | 'linkedin_pdf' | 'personal_site'
        String provenance,             // 'harvested' | 'user_supplied' | 'self_declared'
        String consentBasis,           // 'public_data' | 'user_consent' | 'self_declared'
        Map<String, String> context,   // source-specific params (e.g. {"github_login": "torvalds"})
        String ownerId,                // Clerk subject of the user who owns this entity (nullable)
        boolean onboardingDraft,       // keep the entity hidden until onboarding publication
        String profileMode             // 'supply' | 'demand' for newly created onboarding entities
) {
    public SourceRef(String url, String sourceType, String provenance, String consentBasis,
                     Map<String, String> context, String ownerId) {
        this(url, sourceType, provenance, consentBasis, context, ownerId, false, null);
    }
}
