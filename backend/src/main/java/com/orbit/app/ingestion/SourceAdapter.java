package com.orbit.app.ingestion;

import com.orbit.app.ingestion.dto.EntityProfile;
import com.orbit.app.ingestion.dto.ExternalId;
import com.orbit.app.ingestion.dto.RawContent;
import com.orbit.app.ingestion.dto.SourceRef;

import java.util.List;

/**
 * Strategy interface for ingesting content from a specific external source type.
 * <p>
 * Each source type (GitHub, ORCID/OpenAlex, LinkedIn PDF, personal website) implements
 * this interface as a Spring {@code @Component}. The {@link SourceAdapterRegistry}
 * auto-discovers all implementations via Spring component scanning.
 * <p>
 * Adding a new source = implement this interface + annotate as {@code @Component}.
 * Zero changes to the orchestrator.
 */
public interface SourceAdapter {

    /**
     * The source_type string stored in the sources table (e.g. "github", "orcid_pub").
     */
    String sourceType();

    /**
     * Fetch raw content from the external system. May throw on network/API errors.
     */
    RawContent fetch(SourceRef ref) throws IngestionException;

    /**
     * Extract the high-signal core text from raw content (strip boilerplate, noise).
     * This is the text that gets chunked and embedded.
     */
    String extractHighSignalCore(RawContent raw);

    /**
     * Build the human-readable label for citation chips
     * (e.g. "github.com/{owner}/{repo}", "«{title}» ({year})").
     */
    String buildLabel(RawContent raw);

    /**
     * Extract external identifiers for entity resolution.
     * These are checked against entity_identifiers to find or create the entity.
     */
    List<ExternalId> extractExternalIds(RawContent raw);

    /**
     * Derive profile fields for a new entity (name, headline, org, lat/lng).
     * Only called when no existing entity is found.
     */
    EntityProfile deriveProfile(RawContent raw);

    /**
     * For adapters that produce MULTIPLE sources from one input (GitHub repos,
     * OpenAlex publications), discover the sub-sources. Adapters that produce
     * exactly one source return a single-element list.
     * <p>
     * Default: one source per input (LinkedIn, website, manual).
     */
    default List<SourceRef> discoverSources(SourceRef ref) throws IngestionException {
        return List.of(ref);
    }
}